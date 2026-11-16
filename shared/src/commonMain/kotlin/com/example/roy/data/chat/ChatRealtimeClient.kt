@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.roy.data.chat

import com.example.roy.data.auth.AuthJson
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.chat.ChatRealtimeEvent
import com.example.roy.domain.chat.MeetingChangeKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** One authenticated wire for the active session, with consumer-specific chat delivery. */
internal class ChatRealtimeClient(
    baseUrl: String,
    private val transport: ChatSocketTransport,
    private val sessions: SessionCoordinator,
    applicationScope: CoroutineScope,
) {
    private val url = baseUrl.trimEnd('/').replaceFirst(Regex("^http"), "ws") + "/api/v1/ws"
    private val watchers = MutableStateFlow(emptyMap<Uuid, Watcher>())
    private val broadcast = MutableSharedFlow<ScopedEvent>()

    init {
        applicationScope.launch {
            combine(sessions.state, watchers) { status, observers ->
                (status as? SessionStatus.Active)?.scopeId?.takeIf { id ->
                    observers.values.any { it.scopeId == id }
                }
            }.distinctUntilChanged().collectLatest { scopeId ->
                if (scopeId != null) connectLoop(scopeId)
            }
        }
    }

    fun events(scopeId: Long, chatId: String? = null): Flow<ChatRealtimeEvent> =
        events(scopeId, flowOf(setOfNotNull(chatId)), activeChat = chatId)

    fun events(scopeId: Long, chatIds: Flow<Set<String>>): Flow<ChatRealtimeEvent> =
        events(scopeId, chatIds, activeChat = null)

    private fun events(
        scopeId: Long,
        chatIds: Flow<Set<String>>,
        activeChat: String?,
    ): Flow<ChatRealtimeEvent> = channelFlow {
        val watcherId = Uuid.random()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            broadcast.collect { (eventScope, event) ->
                if (eventScope != scopeId || !isActive(scopeId)) return@collect
                val observer = watchers.value[watcherId] ?: return@collect
                val chatId = when (event) {
                    is ChatRealtimeEvent.MessageCreated -> event.message.chatId
                    is ChatRealtimeEvent.AccessLost -> event.chatId
                    is ChatRealtimeEvent.MeetingChanged -> {
                        if (observer.activeChat != event.chatId) return@collect
                        event.chatId
                    }
                    ChatRealtimeEvent.ChatsChanged, ChatRealtimeEvent.Resync -> null
                    ChatRealtimeEvent.NotificationsChanged -> {
                        if (observer.activeChat != null || observer.chatIds.isNotEmpty()) return@collect
                        null
                    }
                }
                if (chatId == null || chatId in observer.chatIds) send(event)
            }
        }
        val subscriptions = launch {
            chatIds.collect { ids ->
                val verifiedIds = ids.filterTo(linkedSetOf()) { Uuid.parseOrNull(it)?.toString() == it }
                watchers.update { it + (watcherId to Watcher(scopeId, activeChat, verifiedIds)) }
            }
        }
        try {
            // A newly visible consumer needs a snapshot even if another screen owns an already open wire.
            if (isActive(scopeId)) send(ChatRealtimeEvent.Resync)
            sessions.state.first { it != SessionStatus.Active(scopeId) }
        } finally {
            collector.cancel()
            subscriptions.cancel()
            watchers.update { it - watcherId }
        }
    }

    private suspend fun connectLoop(scopeId: Long) {
        var backoffSeconds = 1
        while (isActive(scopeId)) {
            val wire = openAuthorized(scopeId)
            if (wire != null) {
                val openedAt = TimeSource.Monotonic.markNow()
                try {
                    serve(wire, scopeId)
                } catch (ended: ChatSocketEnded) {
                    if (ended.unauthorized) {
                        sessions.currentUser(scopeId)
                        backoffSeconds = 1
                    }
                } finally {
                    wire.close()
                }
                if (openedAt.elapsedNow() >= 10.seconds) backoffSeconds = 1
            }
            if (!isActive(scopeId)) return
            delay(backoffSeconds.seconds)
            backoffSeconds = (backoffSeconds * 2).coerceAtMost(16)
        }
    }

    private suspend fun openAuthorized(scopeId: Long): ChatSocket? {
        var opening: ChatSocket? = null
        try {
            val result = sessions.readProtected(scopeId) { token ->
                val wire = try {
                    transport.open(url, token)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    return@readProtected failure(AuthErrorCode.Network)
                }
                opening = wire
                val hello = withTimeoutOrNull(10.seconds) { wire.incoming.receiveCatching() }
                val frame = hello?.getOrNull()?.let(::decodeFrame)
                if (frame?.field("type") == "hello" && frame.field("heartbeatSeconds") == "30") {
                    AuthResult.Success(wire)
                } else {
                    val rejected = (hello?.exceptionOrNull() as? ChatSocketEnded)?.unauthorized == true
                    wire.close()
                    opening = null
                    failure(if (rejected) AuthErrorCode.AuthRequired else AuthErrorCode.Network)
                }
            }
            return (result as? AuthResult.Success)?.value?.also { opening = null }
        } finally {
            opening?.close()
        }
    }

    private suspend fun serve(wire: ChatSocket, scopeId: Long): Unit = coroutineScope {
        val changes = watchers.map { requestedChats(scopeId, it.values) }.distinctUntilChanged().produceIn(this)
        var subscribed = emptySet<String>()
        var desired = changes.receive()
        try {
            while (isActive(scopeId)) {
                val removed = subscribed - desired
                if (removed.isNotEmpty()) {
                    command(wire, scopeId, "unsubscribe", "unsubscribed", removed, subscribed - removed)
                    subscribed -= removed
                }
                val added = desired - subscribed
                if (added.isNotEmpty()) {
                    val reply = command(wire, scopeId, "subscribe", "subscribed", added, subscribed)
                    val accepted = reply.ids("acceptedChatIds").intersect(added)
                    val rejected = reply.ids("rejectedChatIds").intersect(added)
                    if (accepted.intersect(rejected).isNotEmpty() || accepted + rejected != added) {
                        throw ChatSocketEnded()
                    }
                    subscribed += accepted
                    rejected.forEach { emit(scopeId, ChatRealtimeEvent.AccessLost(it)) }
                }
                emit(scopeId, ChatRealtimeEvent.Resync)

                desired = nextSubscription(wire, scopeId, subscribed, changes)
            }
        } finally {
            changes.cancel()
        }
    }

    private suspend fun nextSubscription(
        wire: ChatSocket,
        scopeId: Long,
        subscribed: Set<String>,
        changes: ReceiveChannel<Set<String>>,
    ): Set<String> {
        while (true) {
            val desired = select<Set<String>?> {
                changes.onReceive { it }
                wire.incoming.onReceiveCatching { result ->
                    val frame = result.getOrNull() ?: throw (result.exceptionOrNull() ?: ChatSocketEnded())
                    receive(scopeId, subscribed, frame)
                    null
                }
            }
            if (desired != null) return desired
        }
    }

    private suspend fun command(
        wire: ChatSocket,
        scopeId: Long,
        type: String,
        replyType: String,
        ids: Set<String>,
        subscribed: Set<String>,
    ): JsonObject {
        val requestId = Uuid.random().toString()
        val request = buildJsonObject {
            put("v", 1)
            put("type", type)
            put("requestId", requestId)
            put("chatIds", JsonArray(ids.map(::JsonPrimitive)))
        }
        if (!wire.send(request.toString())) throw ChatSocketEnded()
        val reply = withTimeoutOrNull(10.seconds) {
            var matching: JsonObject? = null
            while (matching == null) {
                val result = wire.incoming.receiveCatching()
                val text = result.getOrNull() ?: throw (result.exceptionOrNull() ?: ChatSocketEnded())
                val frame = decodeFrame(text)
                if (frame?.field("requestId") == requestId) {
                    if (frame.field("type") != replyType) throw ChatSocketEnded()
                    matching = frame
                } else {
                    receive(scopeId, subscribed, text)
                }
            }
            matching
        }
        return reply ?: throw ChatSocketEnded()
    }

    private suspend fun receive(scopeId: Long, subscribed: Set<String>, text: String) {
        val frame = decodeFrame(text) ?: return
        val scope = frame["scope"] as? JsonObject ?: return
        when (val type = frame.field("type")) {
            "chat.message.created" -> {
                val room = scope.field("id")?.takeIf { scope.field("kind") == "chat" && it in subscribed } ?: return
                val payload = frame["payload"] ?: return
                val message = try {
                    AuthJson.decodeFromJsonElement<ChatMessageDto>(payload).toDomain()
                } catch (_: IllegalArgumentException) {
                    throw ChatSocketEnded()
                }
                if (message.chatId != room) throw ChatSocketEnded()
                emit(scopeId, ChatRealtimeEvent.MessageCreated(message))
            }
            "chat.membership.changed" -> {
                val payload = frame["payload"] as? JsonObject
                val leftChat = payload?.field("chatId")
                if (scope.field("kind") == "user" && payload?.field("action") == "left" && leftChat != null) {
                    emit(scopeId, ChatRealtimeEvent.AccessLost(leftChat))
                }
                emit(scopeId, ChatRealtimeEvent.ChatsChanged)
            }
            "point.changed", "point.deleted" -> {
                emit(scopeId, ChatRealtimeEvent.ChatsChanged)
            }
            "proposal.updated", "booking.changed" -> {
                val room = scope.field("id")?.takeIf { scope.field("kind") == "chat" && it in subscribed } ?: return
                val payload = frame["payload"] as? JsonObject ?: return
                val proposalId = payload.field("proposalId")?.takeIf { Uuid.parseOrNull(it)?.toString() == it } ?: return
                val kind = if (type == "proposal.updated") MeetingChangeKind.ProposalUpdated else MeetingChangeKind.BookingChanged
                val revision = (payload["revision"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }
                if (kind == MeetingChangeKind.ProposalUpdated && revision == null) return
                emit(scopeId, ChatRealtimeEvent.MeetingChanged(room, proposalId, revision, kind))
            }
            "notification.created" -> emit(scopeId, ChatRealtimeEvent.NotificationsChanged)
            "session.revoked" -> {
                if (scope.field("kind") != "user") return
                val userId = scope.field("id") ?: return
                sessions.invalidateRevokedScope(scopeId, userId)
                throw ChatSocketEnded(unauthorized = true)
            }
        }
    }

    private suspend fun emit(scopeId: Long, event: ChatRealtimeEvent) {
        if (isActive(scopeId)) broadcast.emit(ScopedEvent(scopeId, event))
    }

    private fun isActive(scopeId: Long) = sessions.state.value == SessionStatus.Active(scopeId)

    private fun requestedChats(scopeId: Long, all: Collection<Watcher>): Set<String> {
        val watchers = all.filter { it.scopeId == scopeId }
        return (watchers.mapNotNull { it.activeChat } + watchers.flatMap { it.chatIds })
            .distinct().take(100).toSet()
    }

    private data class Watcher(val scopeId: Long, val activeChat: String?, val chatIds: Set<String>)
    private data class ScopedEvent(val scopeId: Long, val event: ChatRealtimeEvent)
}

private fun decodeFrame(text: String): JsonObject? = try {
    (AuthJson.parseToJsonElement(text) as? JsonObject)?.takeIf { it.field("v") == "1" }
} catch (_: IllegalArgumentException) {
    null
}

private fun JsonObject.field(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.ids(key: String): Set<String> =
    (get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()

private fun failure(code: AuthErrorCode) = AuthResult.Failure(AuthError(code))
