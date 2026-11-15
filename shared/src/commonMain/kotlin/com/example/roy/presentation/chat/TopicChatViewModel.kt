package com.example.roy.presentation.chat

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.chat.ChatHistoryRepository
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.domain.chat.ChatReadCursor
import com.example.roy.domain.chat.ChatReadReply
import com.example.roy.domain.chat.ChatRealtimeEvent
import com.example.roy.domain.chat.ChatSendReply
import com.example.roy.domain.chat.ChatWriteRepository
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.points.DetailReply
import com.example.roy.domain.points.PointDetailGateway
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class TopicChatViewModel(
    private val scopeId: Long,
    private val pointId: String,
    private val topicId: String,
    private val chatId: String,
    private val sessions: SessionCoordinator,
    private val history: ChatHistoryRepository,
    private val leaveMembership: PointDetailGateway,
    private val unread: ChatReadCursor,
    writer: ChatWriteRepository,
    private val realtime: () -> Flow<ChatRealtimeEvent>,
    connectivity: Flow<Boolean>,
    private val onResync: () -> Unit,
) : BaseViewModel<TopicChatViewState, TopicChatViewEvent, TopicChatViewAction>(TopicChatViewState()) {
    private var loadJob: Job? = null
    private var earlyMessages = emptyList<ChatMessage>()
    private var olderJob: Job? = null
    private var searchJob: Job? = null
    private var readJob: Job? = null
    private var realtimeJob: Job? = null
    private var isResyncPending = false
    private var isResumed = false
    private var isClosed = false
    private var lastRead = 0L
    private var pendingRead = 0L
    private var completeThrough: Long? = null
    private val composerScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
    private val composer = ChatComposerController(
        composerScope, sessions, writer, scopeId, chatId,
        onSent = ::onCanonicalMessage,
        onAccessLost = ::onSendAccessLost,
    )

    init {
        viewModelScope.launch {
            connectivity.collect { online ->
                if (isClosed || !scopeIsCurrent() || online == viewState.value.isOnline) return@collect
                updateState { it.copy(isOnline = online) }
                if (!online) stopRead()
                else if (isResumed) recheckAccess()
            }
        }
        viewModelScope.launch {
            composer.state.collect { current ->
                if (scopeIsCurrent() && !isClosed) updateState { it.copy(composer = current) }
            }
        }
        viewModelScope.launch {
            sessions.state.collect {
                if (it != SessionStatus.Active(scopeId)) closePrivateState(TopicChatIssue.Session)
            }
        }
    }

    override fun handleEvent(event: TopicChatViewEvent) {
        when (event) {
            TopicChatViewEvent.OnBack -> onBack()
            TopicChatViewEvent.OnResume -> onResume()
            TopicChatViewEvent.OnPause -> onPause()
            TopicChatViewEvent.OnRetry -> onRetry()
            TopicChatViewEvent.OnLoadOlder -> onLoadOlder()
            TopicChatViewEvent.OnOpenSearch -> onOpenSearch()
            TopicChatViewEvent.OnCloseSearch -> onCloseSearch()
            is TopicChatViewEvent.OnSearchQueryChanged -> onSearchQueryChanged(event.query)
            TopicChatViewEvent.OnSearchSubmitted -> onSearchSubmitted()
            TopicChatViewEvent.OnSearchMore -> onSearchMore()
            is TopicChatViewEvent.OnVisibleMessage -> onVisibleMessage(event.seq)
            TopicChatViewEvent.OnLeaveRequested -> onLeaveRequested()
            TopicChatViewEvent.OnLeaveConfirmed -> onLeaveConfirmed()
            TopicChatViewEvent.OnLeaveDismissed -> onLeaveDismissed()
            is TopicChatViewEvent.OnComposerEdited -> onComposerEdited(event.text)
            TopicChatViewEvent.OnSend -> onSend()
            is TopicChatViewEvent.OnReportRequested -> onReportRequested(event.messageId)
            TopicChatViewEvent.OnReportDismissed -> onReportDismissed()
        }
    }

    private fun scopeIsCurrent(): Boolean = sessions.state.value == SessionStatus.Active(scopeId)

    private fun onBack() {
        val state = viewState.value
        when {
            state.isLeaving -> Unit
            state.isLeaveDialogOpen -> onLeaveDismissed()
            state.reportTarget != null -> onReportDismissed()
            state.search != null -> onCloseSearch()
            else -> viewModelScope.launch { sendAction(TopicChatViewAction.CloseScreen) }
        }
    }

    private fun onResume() {
        isResumed = true
        if (isClosed || !scopeIsCurrent()) return
        if (!viewState.value.isLeaving) recheckAccess()
        if (realtimeJob?.isActive == true) return
        realtimeJob = viewModelScope.launch {
            realtime().collect { event ->
                when (event) {
                    ChatRealtimeEvent.Resync, ChatRealtimeEvent.ChatsChanged -> {
                        onResync()
                        recheckAccess()
                    }
                    is ChatRealtimeEvent.MessageCreated -> onCanonicalMessage(event.message)
                    is ChatRealtimeEvent.AccessLost -> verifyAccess()
                }
            }
        }
    }

    private fun onPause() {
        isResumed = false
        realtimeJob?.cancel()
        stopRead()
    }

    private fun onRetry() = recheckAccess()

    private fun verifyAccess() {
        if (isClosed || !scopeIsCurrent() || viewState.value.isLeaving) return
        stopRead()
        searchJob?.cancel()
        updateState { it.copy(isAccessUncertain = true, search = null, reportTarget = null, isLeaveDialogOpen = false) }
        recheckAccess()
    }

    private fun recheckAccess() {
        if (isClosed || !scopeIsCurrent()) return
        isResyncPending = true
        if (loadJob?.isActive == true || viewState.value.isLeaving) return
        if (viewState.value.detail == null) {
            updateState { it.copy(isLoading = true, historyIssue = null) }
            stopRead()
        }
        loadJob = viewModelScope.launch {
            do {
                isResyncPending = false
                if (!loadHistory()) break
            } while (isResyncPending && isResumed && scopeIsCurrent() && !isClosed && !viewState.value.isLeaving)
        }
    }

    private suspend fun loadHistory(): Boolean {
        val reply = sessions.readProtected(scopeId) { history.detail(chatId, it) }
        val detail = readResult(reply, ::setHistoryError) ?: return false
        if (detail.pointId != pointId || detail.topicId != topicId) {
            accessLost(TopicChatIssue.Gone)
            return false
        }

        lastRead = maxOf(lastRead, detail.lastReadMessageSeq)
        composer.syncProfile()

        val since = completeThrough
        if (since == null) {
            val replyPage = sessions.readProtected(scopeId) { history.history(chatId, it) }
            val page = readResult(replyPage, ::setHistoryError) ?: return false
            completeThrough = maxOf(detail.lastMessageSeq, page.items.maxOfOrNull(ChatMessage::messageSeq) ?: 0L)
            val confirmed = mergeChatMessages(page.items, earlyMessages)
            earlyMessages = emptyList()
            updateState { it.copy(messages = confirmed, nextBeforeSeq = page.nextBeforeSeq) }
        } else {
            val newest = viewState.value.messages.firstOrNull()?.messageSeq ?: 0L
            val serverLast = maxOf(detail.lastMessageSeq, newest)
            if (serverLast > since && !catchUp(since, serverLast)) return false
        }
        updateState { it.copy(
            detail = detail, isLoading = false, historyIssue = null,
            isAccessUncertain = it.isAccessUncertain && isResyncPending,
        ) }
        return true
    }

    private fun setHistoryError(issue: TopicChatIssue) {
        updateState { it.copy(isLoading = false, historyIssue = issue) }
    }

    private suspend fun catchUp(since: Long, serverLast: Long): Boolean {
        var after = since
        do {
            val reply = sessions.readProtected(scopeId) { history.history(chatId, it, afterSeq = after) }
            val page = readResult(reply, ::setHistoryError)
                ?: return false
            updateState { it.copy(messages = mergeChatMessages(it.messages, page.items)) }
            completeThrough = maxOf(completeThrough ?: after, page.items.maxOfOrNull(ChatMessage::messageSeq) ?: after)
            after = page.nextAfterSeq ?: break
        } while (true)
        completeThrough = maxOf(serverLast, completeThrough ?: since)
        return true
    }

    private fun onCanonicalMessage(message: ChatMessage) {
        if (isClosed || !scopeIsCurrent() || message.chatId != chatId || viewState.value.isLeaving) return
        // Never reveal live events before the first authorized detail and history read.
        val complete = completeThrough ?: run {
            earlyMessages = mergeChatMessages(earlyMessages, listOf(message)).take(100)
            isResyncPending = true
            return
        }
        updateState { it.copy(messages = mergeChatMessages(it.messages, listOf(message))) }
        if (message.messageSeq == complete + 1L) completeThrough = message.messageSeq
        if (message.messageSeq > complete + 1L && isResumed) recheckAccess()
    }

    private fun onLoadOlder() {
        val state = viewState.value
        val before = state.nextBeforeSeq ?: return
        if (!state.canInteract || state.search != null || state.isLoadingOlder) return
        updateState { it.copy(isLoadingOlder = true, olderIssue = null) }
        olderJob = viewModelScope.launch {
            val reply = sessions.readProtected(scopeId) { history.history(chatId, it, beforeSeq = before) }
            val page = readResult(reply) { issue -> updateState { it.copy(isLoadingOlder = false, olderIssue = issue) } }
                ?: return@launch
            updateState {
                it.copy(
                    messages = mergeChatMessages(it.messages, page.items),
                    nextBeforeSeq = page.nextBeforeSeq,
                    isLoadingOlder = false,
                )
            }
        }
    }

    private fun onOpenSearch() {
        if (!viewState.value.canInteract) return
        stopRead()
        updateState { it.copy(search = TopicChatSearch()) }
    }

    private fun onCloseSearch() {
        searchJob?.cancel()
        updateState { it.copy(search = null) }
    }

    private fun onSearchQueryChanged(query: String) {
        val state = viewState.value
        if (state.search == null || !state.canInteract) return
        searchJob?.cancel()
        updateState { it.copy(search = TopicChatSearch(query = query)) }
        if (query.trim().hasSearchLength()) searchPage(null, 350L)
    }

    private fun onSearchSubmitted() {
        val search = viewState.value.search ?: return
        if (!search.query.trim().hasSearchLength()) {
            updateState { it.copy(search = search.copy(issue = TopicChatIssue.SearchQuery)) }
        } else {
            searchJob?.cancel()
            searchPage(null, 0)
        }
    }

    private fun onSearchMore() {
        val search = viewState.value.search ?: return
        if (!search.isLoading) search.nextCursor?.let { searchPage(it, 0) }
    }

    private fun searchPage(cursor: String?, delayMillis: Long) {
        val state = viewState.value
        val previous = state.search ?: return
        if (!state.canInteract) return
        val query = previous.query.trim()
        updateState { it.copy(search = previous.copy(isLoading = true, issue = null)) }
        searchJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val reply = sessions.readProtected(scopeId) { history.search(chatId, it, query, cursor) }
            val page = readResult(reply) { issue ->
                updateState {
                    it.copy(search = it.search?.copy(isLoading = false, issue = issue))
                }
            } ?: return@launch
            if (viewState.value.search?.query != previous.query) return@launch
            val results = if (cursor == null) page.items else (previous.results + page.items).distinctBy(ChatMessage::id)
            updateState {
                it.copy(search = it.search?.copy(results = results, nextCursor = page.nextCursor,
                    isLoading = false, hasSearched = true))
            }
        }
    }

    private fun onVisibleMessage(seq: Long) {
        val state = viewState.value
        if (!canRead(state) || seq <= lastRead || state.messages.none { it.messageSeq == seq }) return
        pendingRead = maxOf(pendingRead, seq)
        if (readJob?.isActive == true) return
        readJob = viewModelScope.launch {
            delay(250)
            while (canRead(viewState.value) && pendingRead > lastRead) {
                val current = viewState.value
                val next = pendingRead
                val upper = maxOf(current.detail?.lastMessageSeq ?: 0L, current.messages.firstOrNull()?.messageSeq ?: 0L)
                when (val result = unread.advance(next, upper)) {
                    is AuthResult.Success -> {
                        lastRead = maxOf(lastRead, result.value)
                        if (lastRead < next) break
                    }
                    is AuthResult.Failure -> {
                        if (result.error.code != AuthErrorCode.Network) recheckAccess()
                        break
                    }
                }
            }
        }
    }

    private fun canRead(state: TopicChatViewState): Boolean =
        scopeIsCurrent() && isResumed && state.isOnline && state.canInteract && state.search == null &&
            !state.isLeaveDialogOpen && state.reportTarget == null

    private fun stopRead() {
        readJob?.cancel()
        pendingRead = 0L
    }

    private fun onLeaveRequested() {
        if (!viewState.value.canInteract) return
        stopRead()
        updateState { it.copy(isLeaveDialogOpen = true) }
        viewModelScope.launch { sendAction(TopicChatViewAction.OpenLeaveDialog) }
    }

    private fun onLeaveDismissed() {
        if (!viewState.value.isLeaving) updateState { it.copy(isLeaveDialogOpen = false) }
    }

    private fun onLeaveConfirmed() {
        val state = viewState.value
        if (!scopeIsCurrent() || !state.isLeaveDialogOpen || !state.canInteract) return
        updateState { it.copy(isLeaving = true) }
        olderJob?.cancel()
        searchJob?.cancel()
        viewModelScope.launch {
            when (val result = leaveMembership.leave(chatId, scopeId)) {
                is DetailReply.Success, DetailReply.Gone -> {
                    currentCoroutineContext().ensureActive()
                    if (!scopeIsCurrent()) return@launch
                    closePrivateState(TopicChatIssue.NotMember, leaving = true)
                    sendAction(TopicChatViewAction.AccessLost(LeaveTopicResult(pointId, chatId, false)))
                    val pointGone = isPointGone()
                    currentCoroutineContext().ensureActive()
                    if (!scopeIsCurrent()) return@launch
                    if (pointGone) updateState { it.copy(historyIssue = TopicChatIssue.Gone) }
                    sendAction(TopicChatViewAction.Left(LeaveTopicResult(pointId, chatId, pointGone)))
                }
                is DetailReply.AuthFailure -> leaveFailed(result.error.code.toChatIssue())
                DetailReply.Duplicate, DetailReply.InvalidTopic -> leaveFailed(TopicChatIssue.Unavailable)
            }
        }
    }

    private suspend fun leaveFailed(issue: TopicChatIssue) {
        updateState { it.copy(isLeaving = false, isLeaveDialogOpen = false, isLoadingOlder = false) }
        sendAction(TopicChatViewAction.ShowLeaveError(issue))
        if (isResyncPending && isResumed) recheckAccess()
    }

    private fun onSendAccessLost(reply: ChatSendReply) {
        val issue = if (reply == ChatSendReply.Gone) TopicChatIssue.Gone else TopicChatIssue.NotMember
        viewModelScope.launch { accessLost(issue) }
    }

    private fun onComposerEdited(text: String) {
        if (viewState.value.canInteract && scopeIsCurrent()) composer.edit(text)
    }

    private fun onSend() {
        val state = viewState.value
        if (scopeIsCurrent() && isResumed && state.isOnline && state.canInteract && state.search == null &&
            !state.isLeaveDialogOpen && state.reportTarget == null
        ) composer.send()
    }

    private fun onReportRequested(messageId: String) {
        val state = viewState.value
        if (!scopeIsCurrent() || !state.canInteract) return
        val message = (state.search?.results ?: state.messages).firstOrNull { it.id == messageId } ?: return
        val author = message.author ?: return
        val userId = author.id ?: return
        if (message.type != "text" || userId == state.detail?.userId) return
        stopRead()
        updateState { it.copy(reportTarget = ChatReportTarget(userId, author.displayName)) }
    }

    private fun onReportDismissed() {
        updateState { it.copy(reportTarget = null) }
    }

    private suspend fun <T> readResult(result: AuthResult<ChatReadReply<T>>, onError: (TopicChatIssue) -> Unit): T? {
        currentCoroutineContext().ensureActive()
        if (isClosed || !scopeIsCurrent() || viewState.value.isLeaving) return null
        return when (result) {
            is AuthResult.Failure -> null.also { onError(result.error.code.toChatIssue()) }
            is AuthResult.Success -> when (val reply = result.value) {
                is ChatReadReply.Data -> reply.value
                ChatReadReply.Gone -> null.also { accessLost(TopicChatIssue.Gone) }
                ChatReadReply.NotMember -> null.also { accessLost(TopicChatIssue.NotMember) }
            }
        }
    }

    private suspend fun accessLost(issue: TopicChatIssue) {
        if (isClosed) return
        // Stop rendering private data immediately. A public point read distinguishes a deleted
        // point from a live point whose chat access has been revoked.
        closePrivateState(issue)
        sendAction(TopicChatViewAction.AccessLost(LeaveTopicResult(pointId, chatId, false)))
        val pointGone = isPointGone()
        currentCoroutineContext().ensureActive()
        if (!scopeIsCurrent()) return
        if (pointGone) {
            updateState { it.copy(historyIssue = TopicChatIssue.Gone) }
            sendAction(TopicChatViewAction.AccessLost(LeaveTopicResult(pointId, chatId, true)))
        }
    }

    private suspend fun isPointGone(): Boolean {
        val point = leaveMembership.point(pointId)
        return point is PointResult.Failure && point.error == PointError.ResourceGone
    }

    private fun closePrivateState(issue: TopicChatIssue, leaving: Boolean = false) {
        isClosed = true
        earlyMessages = emptyList()
        // The calling request can be the load, pagination, search or leave job. Its guarded
        // response must still notify navigation after private state has been erased.
        realtimeJob?.cancel()
        stopRead()
        composer.revoke()
        composerScope.cancel()
        updateState { TopicChatViewState(isLoading = false, historyIssue = issue, isLeaving = leaving) }
    }
}

internal fun mergeChatMessages(current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> =
    (incoming + current).distinctBy(ChatMessage::id).distinctBy(ChatMessage::messageSeq)
        .sortedByDescending(ChatMessage::messageSeq)

private fun String.hasSearchLength(): Boolean {
    if ('\u0000' in this) return false
    var count = 0
    var offset = 0
    while (offset < length) {
        if (this[offset].isHighSurrogate() && getOrNull(offset + 1)?.isLowSurrogate() == true) offset++
        count++
        offset++
    }
    return count in 3..256
}

private fun AuthErrorCode.toChatIssue(): TopicChatIssue = when (this) {
    AuthErrorCode.Network -> TopicChatIssue.Network
    AuthErrorCode.AuthRequired, AuthErrorCode.SessionExpired -> TopicChatIssue.Session
    else -> TopicChatIssue.Unavailable
}
