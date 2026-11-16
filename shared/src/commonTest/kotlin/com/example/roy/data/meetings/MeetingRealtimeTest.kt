package com.example.roy.data.meetings

import com.example.roy.data.chat.ChatRealtimeClient
import com.example.roy.data.chat.ChatSocket
import com.example.roy.data.chat.ChatSocketTransport
import com.example.roy.domain.auth.AuthCredentials
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.chat.ChatRealtimeEvent
import com.example.roy.domain.chat.MeetingChangeKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeetingRealtimeTest {
    @Test
    fun exposesOnlyMeetingChangesForAnOpenSubscribedRoom() = runTest {
        val sessions = SessionCoordinator(SessionTestRepository(), SessionTestStore())
        sessions.restore()
        sessions.authenticate(AuthCredentials.Login("fixture", "fixture"))
        val scope = (sessions.state.value as SessionStatus.Active).scopeId
        val wire = MeetingSocket()
        val realtime = ChatRealtimeClient(
            "http://127.0.0.1:18080",
            object : ChatSocketTransport {
                override fun open(url: String, accessToken: String): ChatSocket = wire
            },
            sessions,
            backgroundScope,
        )
        val activeRoom = mutableListOf<ChatRealtimeEvent>()
        val list = mutableListOf<ChatRealtimeEvent>()
        backgroundScope.launch { realtime.events(scope, ROOM).collect { activeRoom += it } }
        backgroundScope.launch { realtime.events(scope, flowOf(setOf(ROOM))).collect { list += it } }
        runCurrent()

        wire.meeting("proposal.updated", "chat", ROOM, ""","revision":22,"viewerGoing":true""")
        wire.meeting("booking.changed", "chat", ROOM)
        wire.meeting("proposal.updated", "chat", OTHER_ROOM, ""","revision":23""")
        wire.meeting("booking.changed", "user", ROOM)
        runCurrent()
        assertEquals(
            listOf(
                ChatRealtimeEvent.MeetingChanged(ROOM, ACTIVE_PROPOSAL, 22L, MeetingChangeKind.ProposalUpdated),
                ChatRealtimeEvent.MeetingChanged(ROOM, ACTIVE_PROPOSAL, null, MeetingChangeKind.BookingChanged),
            ),
            activeRoom.filterIsInstance<ChatRealtimeEvent.MeetingChanged>(),
        )
        assertTrue(list.none { it is ChatRealtimeEvent.MeetingChanged })
    }
}

private class MeetingSocket : ChatSocket {
    override val incoming = Channel<String>(Channel.UNLIMITED).apply {
        trySend("""{"v":1,"type":"hello","heartbeatSeconds":30}""")
    }

    override fun send(text: String): Boolean {
        val command = Json.parseToJsonElement(text).jsonObject
        val reply = buildJsonObject {
            put("v", 1)
            put("type", "subscribed")
            put("requestId", command.getValue("requestId"))
            put("acceptedChatIds", command.getValue("chatIds"))
            put("rejectedChatIds", Json.parseToJsonElement("[]"))
        }
        return incoming.trySend(reply.toString()).isSuccess
    }

    fun meeting(type: String, scopeKind: String, room: String, fields: String = "") {
        val frame = """{"v":1,"type":"$type","scope":{"kind":"$scopeKind","id":"$room"},"payload":{"proposalId":"$ACTIVE_PROPOSAL"$fields}}"""
        check(incoming.trySend(frame).isSuccess)
    }

    override fun close() = incoming.cancel()
}

private const val ROOM = "00000000-0000-4000-8000-000000000011"
private const val OTHER_ROOM = "00000000-0000-4000-8000-000000000012"
private const val ACTIVE_PROPOSAL = "00000000-0000-4000-8000-000000000013"
