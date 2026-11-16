package com.example.roy.presentation.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.meetings.MeetingVoterAvatarLoader
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.overlays.RoyDialog
import com.example.roy.presentation.components.overlays.RoySnackbarHost
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.components.overlays.RoySnackbarMessage
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.meetings.MeetingDialogs
import com.example.roy.presentation.meetings.MeetingViewAction
import com.example.roy.presentation.meetings.MeetingViewEvent
import com.example.roy.presentation.meetings.MeetingViewModel
import com.example.roy.presentation.meetings.MeetingVoterAvatar
import com.example.roy.presentation.meetings.PinnedMeeting
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
import roy.shared.generated.resources.meetings_title
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
    meetings: MeetingViewModel? = null,
    loadVoterAvatar: MeetingVoterAvatarLoader? = null,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(viewModel) { viewModel::handleEvent }
    val meetingState = meetings?.viewState?.collectAsStateWithLifecycle()?.value
    val meetingEvent = remember(meetings) { meetings?.let { it::handleEvent } }
    val viewerId = viewState.detail?.userId
    val isMembershipDenied = when (viewState.historyIssue) {
        TopicChatIssue.NotMember, TopicChatIssue.Gone, TopicChatIssue.Session -> true
        else -> false
    }
    val chatAuthorized = viewerId != null && !viewState.isAccessUncertain && !viewState.isLeaving && !isMembershipDenied
    val isMeetingOverlayOpen = chatAuthorized && meetingState?.sheet != null
    LaunchedEffect(meetings, chatAuthorized, viewerId, viewState.isOnline) {
        meetingEvent?.invoke(MeetingViewEvent.OnAccessChanged(chatAuthorized, viewerId, viewState.isOnline))
    }
    LaunchedEffect(viewModel, isMeetingOverlayOpen) {
        eventHandler(TopicChatViewEvent.OnMeetingOverlayChanged(isMeetingOverlayOpen))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (chatAuthorized) meetingEvent?.invoke(MeetingViewEvent.OnRefresh)
    }
    if (meetings != null) {
        CollectMviActions(meetings.viewAction) { action ->
            if (action == MeetingViewAction.RefreshChatHistory && chatAuthorized) {
                eventHandler(TopicChatViewEvent.OnRetry)
            }
        }
    }
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
    BackHandler(
        enabled = !isMeetingOverlayOpen && viewState.search == null && !viewState.isLeaveDialogOpen && viewState.reportTarget == null,
    ) {
        eventHandler(TopicChatViewEvent.OnBack)
    }

    Box {
        val action: (@Composable () -> Unit)? = if (chatAuthorized && meetingEvent != null) {
            {
                TertiaryButton(
                    label = stringResource(Res.string.meetings_title),
                    onClick = { meetingEvent(MeetingViewEvent.OnMeetingsRequested) },
                    isEnabled = viewState.canInteract,
                )
            }
        } else null
        val openProposal: ((String) -> Unit)? = if (chatAuthorized && meetingEvent != null) {
            { meetingEvent(MeetingViewEvent.OnProposalSelected(it)) }
        } else null
        val pinned = meetingState?.currentProposal
        val pinnedContent: (@Composable () -> Unit)? = if (pinned != null && openProposal != null) {
            { PinnedMeeting(pinned) { openProposal(pinned.id) } }
        } else null
        TopicChatView(
            viewState = viewState,
            meetingAction = action,
            pinnedMeeting = pinnedContent,
            onOpenMeeting = openProposal,
            isHistoryBehindMeetingSheet = isMeetingOverlayOpen,
            eventHandler = eventHandler,
        )
        RoySnackbarHost(snackbar, Modifier.align(Alignment.Center).padding(RoyTheme.dimensions.spacing.medium))
    }
    if (chatAuthorized && meetingState != null && meetingEvent != null) {
        MeetingDialogs(meetingState, meetingEvent) { voter -> MeetingVoterAvatar(voter, loadVoterAvatar) }
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
