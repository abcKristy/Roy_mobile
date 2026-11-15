package com.example.roy.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.chat_empty
import roy.shared.generated.resources.chat_history_loading
import roy.shared.generated.resources.chat_leave
import roy.shared.generated.resources.chat_search
import roy.shared.generated.resources.protected_back
import roy.shared.generated.resources.chat_title

@Composable
internal fun TopicChatView(viewState: TopicChatViewState, eventHandler: (TopicChatViewEvent) -> Unit) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val spacing = RoyTheme.dimensions.spacing
    val listState = rememberLazyListState()
    val visibleDetail = viewState.detail.takeUnless { viewState.isAccessUncertain }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background)
            .navigationBarsPadding().imePadding().padding(horizontal = spacing.medium),
    ) {
        TertiaryButton(
            label = stringResource(Res.string.protected_back),
            onClick = { eventHandler(TopicChatViewEvent.OnBack) },
            isEnabled = !viewState.isLeaving,
        )
        BasicText(
            text = visibleDetail?.topicLabel ?: stringResource(Res.string.chat_title),
            style = typography.headingMedium.copy(color = colors.textPrimary),
            modifier = Modifier.semantics { heading() },
        )
        visibleDetail?.let { chat ->
            BasicText(chat.address, style = typography.bodySmall.copy(color = colors.textSecondary))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                TertiaryButton(
                    label = stringResource(Res.string.chat_search),
                    onClick = { eventHandler(TopicChatViewEvent.OnOpenSearch) },
                    isEnabled = viewState.canInteract,
                )
                TertiaryButton(
                    label = stringResource(Res.string.chat_leave),
                    onClick = { eventHandler(TopicChatViewEvent.OnLeaveRequested) },
                    isEnabled = viewState.canInteract,
                )
            }
        }
        if (viewState.isLoading && visibleDetail != null) {
            LoadingState(LoadingMode.Inline, stringResource(Res.string.chat_history_loading))
        }
        viewState.historyIssue?.takeIf { !viewState.isAccessUncertain && viewState.messages.isNotEmpty() }?.let { issue ->
            ErrorState(stringResource(issue.stringResource), true, { eventHandler(TopicChatViewEvent.OnRetry) })
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                !viewState.isAccessUncertain && viewState.messages.isNotEmpty() -> ChatHistoryList(viewState, listState, eventHandler)
                viewState.isLoading -> LoadingState(LoadingMode.Fullscreen, stringResource(Res.string.chat_history_loading))
                viewState.historyIssue != null -> {
                    val issue = viewState.historyIssue
                    val mayRetry = issue == TopicChatIssue.Network || issue == TopicChatIssue.Unavailable
                    if (mayRetry) {
                        ErrorState(stringResource(issue.stringResource), true, { eventHandler(TopicChatViewEvent.OnRetry) })
                    } else {
                        BasicText(
                            text = stringResource(issue.stringResource),
                            style = typography.body.copy(color = colors.textPrimary),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                viewState.isAccessUncertain -> LoadingState(LoadingMode.Fullscreen, stringResource(Res.string.chat_history_loading))
                else -> BasicText(stringResource(Res.string.chat_empty), style = typography.body.copy(color = colors.textSecondary))
            }
        }
        if (visibleDetail != null) {
            TopicChatComposer(
                state = viewState.composer,
                isEnabled = viewState.canInteract && !viewState.isLeaveDialogOpen && viewState.reportTarget == null,
                isOnline = viewState.isOnline,
                eventHandler = eventHandler,
            )
        }
    }
}
