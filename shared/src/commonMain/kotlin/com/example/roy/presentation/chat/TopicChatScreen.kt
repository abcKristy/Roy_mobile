package com.example.roy.presentation.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.presentation.components.overlays.RoyDialog
import com.example.roy.presentation.components.overlays.RoySnackbarHost
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.components.overlays.RoySnackbarMessage
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.chat_leave
import roy.shared.generated.resources.chat_leave_body
import roy.shared.generated.resources.chat_leave_title
import roy.shared.generated.resources.chat_not_member
import roy.shared.generated.resources.chat_resource_gone
import roy.shared.generated.resources.chat_search_hint
import roy.shared.generated.resources.point_detail_cancel
import roy.shared.generated.resources.point_detail_network
import roy.shared.generated.resources.point_detail_session
import roy.shared.generated.resources.protected_unavailable

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TopicChatScreen(
    viewModel: TopicChatViewModel,
    onBack: () -> Unit,
    onLeft: (LeaveTopicResult) -> Unit,
    onAccessLost: (LeaveTopicResult) -> Unit = {},
    reportContent: @Composable (ChatReportTarget, () -> Unit) -> Unit = { _, _ -> },
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(viewModel) { viewModel::handleEvent }
    val snackbar = remember { RoySnackbarHostState() }
    var snackbarId by remember { mutableLongStateOf(0L) }
    val issues = TopicChatIssue.entries.associateWith { stringResource(it.stringResource) }

    LifecycleResumeEffect(viewModel) {
        eventHandler(TopicChatViewEvent.OnResume)
        onPauseOrDispose { eventHandler(TopicChatViewEvent.OnPause) }
    }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            TopicChatViewAction.CloseScreen -> onBack()
            TopicChatViewAction.OpenLeaveDialog -> Unit
            is TopicChatViewAction.ShowLeaveError -> snackbar.show(
                RoySnackbarMessage(++snackbarId, issues.getValue(action.issue)),
            )
            is TopicChatViewAction.Left -> onLeft(action.result)
            is TopicChatViewAction.AccessLost -> onAccessLost(action.result)
        }
    }
    BackHandler(enabled = viewState.search == null && !viewState.isLeaveDialogOpen && viewState.reportTarget == null) {
        eventHandler(TopicChatViewEvent.OnBack)
    }

    Box {
        TopicChatView(viewState, eventHandler)
        RoySnackbarHost(snackbar, Modifier.align(Alignment.Center).padding(RoyTheme.dimensions.spacing.medium))
    }
    if (viewState.isLeaveDialogOpen) {
        RoyDialog(
            title = stringResource(Res.string.chat_leave_title),
            body = stringResource(Res.string.chat_leave_body),
            confirmText = stringResource(Res.string.chat_leave),
            cancelText = stringResource(Res.string.point_detail_cancel),
            onConfirm = { eventHandler(TopicChatViewEvent.OnLeaveConfirmed) },
            onCancel = { eventHandler(TopicChatViewEvent.OnLeaveDismissed) },
            isSubmitting = viewState.isLeaving,
            isDestructive = true,
        )
    }
    viewState.search?.let { ChatSearchSheet(it, viewState.detail?.userId, eventHandler) }
    viewState.reportTarget?.let { target ->
        reportContent(target) { eventHandler(TopicChatViewEvent.OnReportDismissed) }
    }
}

internal val TopicChatIssue.stringResource: StringResource
    get() = when (this) {
        TopicChatIssue.Network -> Res.string.point_detail_network
        TopicChatIssue.Unavailable -> Res.string.protected_unavailable
        TopicChatIssue.NotMember -> Res.string.chat_not_member
        TopicChatIssue.Gone -> Res.string.chat_resource_gone
        TopicChatIssue.Session -> Res.string.point_detail_session
        TopicChatIssue.SearchQuery -> Res.string.chat_search_hint
    }
