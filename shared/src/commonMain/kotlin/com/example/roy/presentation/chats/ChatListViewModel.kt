package com.example.roy.presentation.chats

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.offline.ChatCacheStore
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.chat.ChatListItem
import com.example.roy.domain.chat.ChatListOperations
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.Instant

internal data class ChatListViewState(
    val rows: List<ChatListItem> = emptyList(),
    val isLoading: Boolean = true,
    val nextCursor: String? = null,
    val error: AuthErrorCode? = null,
    val isOnline: Boolean = true,
    val cachedFetchedAt: Instant? = null,
)

internal sealed class ChatListViewEvent {
    data object Refresh : ChatListViewEvent()
    data object Retry : ChatListViewEvent()
    data object LoadMore : ChatListViewEvent()
    data object OpenMap : ChatListViewEvent()
    data class OpenChat(val chatId: String) : ChatListViewEvent()
    data class MembershipLost(val chatId: String) : ChatListViewEvent()
}

internal sealed class ChatListViewAction {
    data object OpenMap : ChatListViewAction()
    data class OpenChat(val row: ChatListItem) : ChatListViewAction()
}

internal class ChatListViewModel(
    private val scopeId: Long,
    private val operations: ChatListOperations,
    private val offlineCache: ChatCacheStore? = null,
    private val sessions: SessionCoordinator? = null,
    connectivity: Flow<Boolean> = flowOf(true),
) : BaseViewModel<ChatListViewState, ChatListViewEvent, ChatListViewAction>(ChatListViewState()) {
    private var job: Job? = null
    private var refreshQueued = false
    private var loadedPages = 1
    private var failedLoadingMore = false

    init {
        if (sessions != null) viewModelScope.launch {
            sessions.state.collect { status ->
                if (status == SessionStatus.Active(scopeId)) return@collect
                job?.cancel()
                job = null
                refreshQueued = false
                updateState { ChatListViewState(isLoading = false, isOnline = it.isOnline) }
            }
        }
        viewModelScope.launch {
            connectivity.collect { online ->
                if (viewState.value.isOnline == online) return@collect
                job?.cancel()
                job = null
                refreshQueued = false
                updateState { it.copy(isOnline = online) }
                if (sessions == null || sessions.state.value == SessionStatus.Active(scopeId)) refresh()
            }
        }
    }

    override fun handleEvent(event: ChatListViewEvent) {
        when (event) {
            ChatListViewEvent.Refresh -> refresh()
            ChatListViewEvent.Retry -> if (failedLoadingMore) loadMore() else refresh()
            ChatListViewEvent.LoadMore -> loadMore()
            is ChatListViewEvent.MembershipLost -> remove(event.chatId)
            ChatListViewEvent.OpenMap -> viewModelScope.launch { sendAction(ChatListViewAction.OpenMap) }
            is ChatListViewEvent.OpenChat -> {
                val row = viewState.value.rows.firstOrNull { it.chatId == event.chatId } ?: return
                viewModelScope.launch { sendAction(ChatListViewAction.OpenChat(row)) }
            }
        }
    }

    private fun remove(chatId: String) {
        job?.cancel()
        job = null
        refreshQueued = false
        failedLoadingMore = false
        viewModelScope.launch {
            offlineCache?.let { cache -> sessions?.withOfflineAccount(scopeId) { cache.evictRoom(it, chatId) } }
        }
        updateState { state ->
            state.copy(rows = state.rows.filterNot { it.chatId == chatId }, isLoading = false, nextCursor = null)
        }
    }

    private fun refresh() {
        if (job?.isActive == true) {
            refreshQueued = true
            return
        }
        failedLoadingMore = false
        load(initialCursor = null, pageCount = loadedPages, replace = true)
    }

    private fun loadMore() {
        if (job?.isActive == true || !viewState.value.isOnline || viewState.value.cachedFetchedAt != null) return
        val cursor = viewState.value.nextCursor ?: return
        failedLoadingMore = true
        load(initialCursor = cursor, pageCount = 1, replace = false)
    }

    private fun load(initialCursor: String?, pageCount: Int, replace: Boolean) {
        updateState { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            if (!viewState.value.isOnline) {
                restoreOfflineList()
                job = null
                return@launch
            }
            var cursor = initialCursor
            val rows = if (replace) mutableMapOf() else viewState.value.rows.associateByTo(mutableMapOf()) { it.chatId }
            val visited = mutableSetOf<String>()
            var pages = 0
            var error: AuthErrorCode? = null
            do {
                if (cursor != null && !visited.add(cursor)) {
                    error = AuthErrorCode.UnexpectedResponse
                    break
                }
                val result = operations.page(scopeId, cursor)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is AuthResult.Failure -> {
                        error = result.error.code
                        break
                    }
                    is AuthResult.Success -> {
                        result.value.items.forEach { rows[it.chatId] = it }
                        cursor = result.value.nextCursor
                        pages++
                    }
                }
            } while (cursor != null && pages < pageCount)
            if (error != null) {
                if (error == AuthErrorCode.Network) restoreOfflineList()
                else updateState { it.copy(isLoading = false, error = error) }
            } else {
                loadedPages = if (replace) pages else loadedPages + pages
                failedLoadingMore = false
                val ordered = rows.values.sortedWith(compareByDescending<ChatListItem> {
                            it.lastMessageAt ?: it.updatedAt
                        }.thenByDescending { it.chatId })
                offlineCache?.let { cache ->
                    sessions?.withOfflineAccount(scopeId) { cache.saveChatList(it, ordered, cursor == null, replace) }
                }
                currentCoroutineContext().ensureActive()
                updateState {
                    it.copy(
                        rows = ordered,
                        isLoading = false,
                        nextCursor = cursor,
                        cachedFetchedAt = null,
                        error = null,
                    )
                }
            }
            job = null
            if (refreshQueued) {
                refreshQueued = false
                refresh()
            }
        }
    }

    private suspend fun restoreOfflineList() {
        val cached = offlineCache?.let { cache -> sessions?.withOfflineAccount(scopeId) { cache.readChatList(it) } }
        currentCoroutineContext().ensureActive()
        updateState {
            if (cached == null) it.copy(isLoading = false, error = AuthErrorCode.Network, nextCursor = null)
            else it.copy(rows = cached.rows, cachedFetchedAt = cached.fetchedAt, nextCursor = null, isLoading = false, error = null)
        }
    }
}
