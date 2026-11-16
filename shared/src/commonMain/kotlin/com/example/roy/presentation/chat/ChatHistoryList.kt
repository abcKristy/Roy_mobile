package com.example.roy.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.chat_history_loading
import roy.shared.generated.resources.chat_history_more

@Composable
internal fun ChatHistoryList(
    viewState: TopicChatViewState,
    listState: LazyListState,
    eventHandler: (TopicChatViewEvent) -> Unit,
    isHistoryBehindMeetingSheet: Boolean = false,
    onOpenMeeting: ((String) -> Unit)? = null,
) {
    val spacing = RoyTheme.dimensions.spacing
    val messagesById by rememberUpdatedState(remember(viewState.messages) { viewState.messages.associateBy(ChatMessage::id) })
    val newestId = viewState.messages.firstOrNull()?.id
    var lastNewestId by remember { mutableStateOf(newestId) }
    SideEffect {
        if (newestId != lastNewestId && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            listState.requestScrollToItem(0)
        }
        lastNewestId = newestId
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canAdvance = !isHistoryBehindMeetingSheet && viewState.isOnline && viewState.canInteract && viewState.search == null &&
        !viewState.isLeaveDialogOpen && viewState.reportTarget == null
    LaunchedEffect(listState, lifecycle, canAdvance) {
        if (!canAdvance) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                val layout = listState.layoutInfo
                layout.visibleItemsInfo.asSequence()
                    .filter { it.offset < layout.viewportEndOffset && it.offset + it.size > layout.viewportStartOffset }
                    .mapNotNull { item -> messagesById[item.key]?.messageSeq }
                    .maxOrNull()
            }.collect { seq -> seq?.let { eventHandler(TopicChatViewEvent.OnVisibleMessage(it)) } }
        }
    }
    LaunchedEffect(listState, viewState.nextBeforeSeq, viewState.olderIssue, canAdvance) {
        if (viewState.nextBeforeSeq == null || viewState.olderIssue != null || !canAdvance) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.any { it.index >= info.totalItemsCount - 3 }
        }.collect { nearOldest ->
            if (nearOldest) eventHandler(TopicChatViewEvent.OnLoadOlder)
        }
    }

    // With newer items first and reverse layout, older pages grow past the current
    // viewport. Stable keys let Compose retain the anchor across incoming live messages.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        items(viewState.messages, key = ChatMessage::id) { message ->
            TopicChatMessageCard(
                message = message,
                currentUserId = viewState.detail?.userId,
                isReportEnabled = viewState.canInteract,
                eventHandler = eventHandler,
                onOpenMeeting = onOpenMeeting.takeIf { viewState.canInteract },
            )
        }
        if (viewState.nextBeforeSeq != null) {
            item(key = "older-page") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        viewState.isLoadingOlder -> LoadingState(
                            LoadingMode.Inline, stringResource(Res.string.chat_history_loading),
                        )
                        viewState.olderIssue != null -> ErrorState(
                            safeMessage = stringResource(viewState.olderIssue.stringResource),
                            isRetryEnabled = viewState.canInteract,
                            onRetry = { eventHandler(TopicChatViewEvent.OnLoadOlder) },
                        )
                        else -> TertiaryButton(
                            label = stringResource(Res.string.chat_history_more),
                            onClick = { eventHandler(TopicChatViewEvent.OnLoadOlder) },
                            isEnabled = viewState.canInteract,
                        )
                    }
                }
            }
        }
    }
}
