package com.example.roy.presentation.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.roy.domain.chat.ChatListItem
import com.example.roy.domain.chat.ChatRealtimeEvent
import com.example.roy.presentation.core.CollectMviActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun ChatListScreen(
    viewModel: ChatListViewModel,
    realtime: (Flow<Set<String>>) -> Flow<ChatRealtimeEvent>,
    onOpenChat: (ChatListItem) -> Unit,
    onOpenMap: () -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val eventHandler = remember(viewModel) { viewModel::handleEvent }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            ChatListViewAction.OpenMap -> onOpenMap()
            is ChatListViewAction.OpenChat -> onOpenChat(action.row)
        }
    }
    LaunchedEffect(viewModel, lifecycle, realtime) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            eventHandler(ChatListViewEvent.OnRefresh)
            val chatIds = viewModel.viewState.map { viewState ->
                viewState.rows.mapTo(mutableSetOf()) { it.chatId }.toSet()
            }.distinctUntilChanged()
            realtime(chatIds).collect { event ->
                if (event is ChatRealtimeEvent.AccessLost) {
                    eventHandler(ChatListViewEvent.OnMembershipLost(event.chatId))
                }
                eventHandler(ChatListViewEvent.OnRefresh)
            }
        }
    }
    ChatListView(viewState, eventHandler)
}
