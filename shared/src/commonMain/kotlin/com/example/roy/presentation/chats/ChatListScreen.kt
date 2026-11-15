package com.example.roy.presentation.chats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.chat.ChatListItem
import com.example.roy.domain.chat.ChatRealtimeEvent
import com.example.roy.domain.chat.chatUnreadBadge
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.feedback.EmptyCourtyardIllustration
import com.example.roy.presentation.components.feedback.EmptyState
import com.example.roy.presentation.components.feedback.EmptyStateAction
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.map.mapTopicIcon
import com.example.roy.presentation.core.formatLocalDateTime
import com.example.roy.presentation.theme.RoyTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.chat_list_error
import roy.shared.generated.resources.chat_list_load_more
import roy.shared.generated.resources.chat_list_loading
import roy.shared.generated.resources.chat_list_no_messages
import roy.shared.generated.resources.chat_list_read_error
import roy.shared.generated.resources.chat_list_unread
import roy.shared.generated.resources.empty_chats_body
import roy.shared.generated.resources.empty_chats_open_map
import roy.shared.generated.resources.empty_chats_title
import roy.shared.generated.resources.tab_chats

@Composable
internal fun ChatListScreen(
    viewModel: ChatListViewModel,
    realtime: (Flow<Set<String>>) -> Flow<ChatRealtimeEvent>,
    onOpenChat: (ChatListItem) -> Unit,
    onOpenMap: () -> Unit,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
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
            eventHandler(ChatListViewEvent.Refresh)
            val chatIds = viewModel.viewState.map { state ->
                state.rows.mapTo(mutableSetOf()) { it.chatId }.toSet()
            }.distinctUntilChanged()
            realtime(chatIds).collect { event ->
                if (event is ChatRealtimeEvent.AccessLost) {
                    eventHandler(ChatListViewEvent.MembershipLost(event.chatId))
                }
                eventHandler(ChatListViewEvent.Refresh)
            }
        }
    }
    ChatListView(state, eventHandler)
}

@Composable
internal fun ChatListView(
    viewState: ChatListViewState,
    eventHandler: (ChatListViewEvent) -> Unit,
) {
    val spacing = RoyTheme.dimensions.spacing
    if (viewState.rows.isEmpty()) {
        when {
            viewState.isLoading -> LoadingState(LoadingMode.Fullscreen, stringResource(Res.string.chat_list_loading))
            viewState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(chatListError(viewState.error), true, { eventHandler(ChatListViewEvent.Retry) })
            }
            else -> EmptyChatList { eventHandler(ChatListViewEvent.OpenMap) }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        item {
            BasicText(
                stringResource(Res.string.tab_chats),
                Modifier.semantics { heading() },
                style = RoyTheme.typography.headingMedium.copy(color = RoyTheme.colors.textPrimary),
            )
        }
        items(viewState.rows, key = { it.chatId }) { row ->
            ChatListRow(row) { eventHandler(ChatListViewEvent.OpenChat(row.chatId)) }
        }
        item {
            when {
                viewState.isLoading -> LoadingState(LoadingMode.Inline, stringResource(Res.string.chat_list_loading))
                viewState.error != null -> ErrorState(
                    chatListError(viewState.error), true, { eventHandler(ChatListViewEvent.Retry) },
                )
                viewState.nextCursor != null -> SecondaryButton(
                    text = stringResource(Res.string.chat_list_load_more),
                    onClick = { eventHandler(ChatListViewEvent.LoadMore) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun EmptyChatList(onOpenMap: () -> Unit) {
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(RoyTheme.dimensions.spacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            title = stringResource(Res.string.empty_chats_title),
            body = stringResource(Res.string.empty_chats_body),
            modifier = Modifier.widthIn(max = 360.dp),
            illustration = { EmptyCourtyardIllustration() },
            action = EmptyStateAction(stringResource(Res.string.empty_chats_open_map), onOpenMap),
        )
    }
}

@Composable
private fun ChatListRow(item: ChatListItem, onClick: () -> Unit) {
    val dimensions = RoyTheme.dimensions
    val colors = RoyTheme.colors
    val type = RoyTheme.typography
    val shape = RoundedCornerShape(dimensions.radii.card)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.minimumTouchTarget)
            .clip(shape).background(colors.surface).clickable(role = Role.Button, onClick = onClick)
            .padding(dimensions.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacing.compact),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(mapTopicIcon(item.presetKey)),
            contentDescription = null,
            modifier = Modifier.size(dimensions.iconSize),
            colorFilter = ColorFilter.tint(colors.textPrimary),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimensions.spacing.extraSmall)) {
            BasicText(item.topic, style = type.headingSmall.copy(color = colors.textPrimary))
            BasicText(item.address, style = type.bodySmall.copy(color = colors.textSecondary))
            BasicText(
                item.preview ?: stringResource(Res.string.chat_list_no_messages),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = type.body.copy(color = colors.textPrimary),
            )
            item.lastMessageAt?.let {
                BasicText(formatLocalDateTime(it), style = type.caption.copy(color = colors.textSecondary))
            }
        }
        chatUnreadBadge(item.unreadCount)?.let { badge ->
            val description = stringResource(Res.string.chat_list_unread, badge)
            BasicText(
                badge,
                Modifier.background(colors.secondary, CircleShape)
                    .padding(horizontal = dimensions.spacing.small, vertical = dimensions.spacing.extraSmall)
                    .clearAndSetSemantics { contentDescription = description },
                style = type.caption.copy(color = colors.textPrimary),
            )
        }
    }
}

@Composable
private fun chatListError(code: AuthErrorCode): String = stringResource(
    if (code == AuthErrorCode.Network) Res.string.chat_list_read_error else Res.string.chat_list_error,
)
