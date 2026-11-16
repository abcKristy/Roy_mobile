package com.example.roy.presentation.meetings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.roy.domain.meetings.MeetingOverlap
import com.example.roy.domain.meetings.MeetingVote
import com.example.roy.presentation.components.DestructiveButton
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.chat_author_deleted
import roy.shared.generated.resources.meetings_book
import roy.shared.generated.resources.meetings_book_confirm_body
import roy.shared.generated.resources.meetings_book_confirm_title
import roy.shared.generated.resources.meetings_close_booked_confirm_body
import roy.shared.generated.resources.meetings_close_confirm_body
import roy.shared.generated.resources.meetings_close_confirm_title
import roy.shared.generated.resources.meetings_close_proposal
import roy.shared.generated.resources.meetings_create
import roy.shared.generated.resources.meetings_detail
import roy.shared.generated.resources.meetings_empty
import roy.shared.generated.resources.meetings_history
import roy.shared.generated.resources.meetings_history_empty
import roy.shared.generated.resources.meetings_loading
import roy.shared.generated.resources.meetings_more
import roy.shared.generated.resources.meetings_overlap_body
import roy.shared.generated.resources.meetings_overlap_continue
import roy.shared.generated.resources.meetings_overlap_title
import roy.shared.generated.resources.meetings_pinned
import roy.shared.generated.resources.meetings_title
import roy.shared.generated.resources.meetings_voters
import roy.shared.generated.resources.meetings_voters_empty
import roy.shared.generated.resources.meetings_voters_loading
import roy.shared.generated.resources.meetings_voters_more
import roy.shared.generated.resources.point_detail_cancel
import roy.shared.generated.resources.protected_back

@Composable
internal fun MeetingDialogs(
    viewState: MeetingViewState,
    eventHandler: (MeetingViewEvent) -> Unit,
    avatarContent: @Composable (MeetingVote) -> Unit = { MeetingVoterAvatar(it) },
) {
    val isBusy = viewState.isCreating || viewState.busyProposalId != null
    val dismiss = { eventHandler(MeetingViewEvent.OnDismiss) }
    val proposal = viewState.selectedProposal
    when (val sheet = viewState.sheet) {
        null -> Unit
        MeetingSheet.List -> MeetingListSheet(viewState, eventHandler)
        MeetingSheet.Create -> MeetingSheetFrame(
            title = stringResource(Res.string.meetings_create),
            onDismiss = dismiss,
            isDismissEnabled = !isBusy,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.small),
            ) {
                viewState.issue?.let { MeetingIssueText(it) }
                MeetingCreateFields(viewState, eventHandler)
            }
        }
        is MeetingSheet.Detail -> MeetingSheetFrame(
            title = stringResource(Res.string.meetings_detail),
            onDismiss = dismiss,
            isDismissEnabled = !isBusy,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.small),
            ) {
                if (proposal != null && proposal.id == sheet.proposalId) {
                    viewState.issue?.let { MeetingIssueText(it) }
                    MeetingProposalCard(proposal, viewState, eventHandler)
                } else {
                    val issue = viewState.issue
                    when (issue) {
                        null -> LoadingState(LoadingMode.Inline, stringResource(Res.string.meetings_loading))
                        MeetingIssue.Gone, MeetingIssue.NotMember -> MeetingIssueText(issue)
                        else -> ErrorState(stringResource(issue.resource), viewState.isOnline, onRetry = {
                            eventHandler(MeetingViewEvent.OnRefresh)
                        })
                    }
                }
            }
        }
        is MeetingSheet.Voters -> MeetingVotersSheet(viewState, eventHandler, avatarContent)
        is MeetingSheet.Booking -> if (proposal != null && proposal.id == sheet.proposalId) {
            MeetingConfirmDialog(
                title = stringResource(Res.string.meetings_book_confirm_title),
                body = stringResource(Res.string.meetings_book_confirm_body, meetingInterval(proposal.startsAt, proposal.endsAt)),
                confirmLabel = stringResource(Res.string.meetings_book),
                issue = viewState.issue,
                isDestructive = false,
                isEnabled = viewState.isOnline && proposal.id in viewState.bookableIds,
                onConfirm = { eventHandler(MeetingViewEvent.OnBookConfirmed) },
                onDismiss = dismiss,
                isSubmitting = isBusy,
            )
        } else {
            MeetingMissingSelection(viewState, dismiss)
        }
        is MeetingSheet.Overlap -> MeetingOverlapDialog(sheet.proposalId, sheet.conflicts, viewState, eventHandler)
        is MeetingSheet.Close -> if (proposal != null && proposal.id == sheet.proposalId) {
            val message = if (proposal.booking?.status == "active") {
                Res.string.meetings_close_booked_confirm_body
            } else {
                Res.string.meetings_close_confirm_body
            }
            MeetingConfirmDialog(
                title = stringResource(Res.string.meetings_close_confirm_title),
                body = stringResource(message, meetingInterval(proposal.startsAt, proposal.endsAt)),
                confirmLabel = stringResource(Res.string.meetings_close_proposal),
                issue = viewState.issue,
                isEnabled = viewState.isOnline && proposal.id in viewState.closableIds,
                onConfirm = { eventHandler(MeetingViewEvent.OnCloseConfirmed) },
                onDismiss = dismiss,
                isSubmitting = isBusy,
                isDestructive = true,
            )
        } else {
            MeetingMissingSelection(viewState, dismiss)
        }
    }
}

@Composable
private fun MeetingListSheet(viewState: MeetingViewState, eventHandler: (MeetingViewEvent) -> Unit) {
    val spacing = RoyTheme.dimensions.spacing
    val pinned = viewState.currentProposal
    val history = viewState.proposals.filterNot { it.id == pinned?.id }
    val isBusy = viewState.busyProposalId != null || viewState.isCreating
    MeetingSheetFrame(
        title = stringResource(Res.string.meetings_title),
        onDismiss = { eventHandler(MeetingViewEvent.OnDismiss) },
        isDismissEnabled = !isBusy,
    ) {
        PrimaryButton(
            text = stringResource(Res.string.meetings_create),
            onClick = { eventHandler(MeetingViewEvent.OnCreateRequested) },
            modifier = Modifier.fillMaxWidth(),
            isEnabled = viewState.isOnline && !isBusy,
        )
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            if (pinned != null) {
                item(key = "meeting-pinned") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        MeetingHeading(stringResource(Res.string.meetings_pinned))
                        MeetingProposalCard(pinned, viewState, eventHandler)
                    }
                }
            }
            if (pinned != null && history.isNotEmpty()) {
                item(key = "meeting-history-title") { MeetingHeading(stringResource(Res.string.meetings_history)) }
            }
            items(history, key = { it.id }) { proposal ->
                MeetingProposalCard(proposal, viewState, eventHandler)
            }
            item(key = "meeting-history-footer") {
                val issue = viewState.issue
                when {
                    viewState.isLoading -> LoadingState(LoadingMode.Inline, stringResource(Res.string.meetings_loading))
                    issue != null && (issue == MeetingIssue.InvalidCursor ||
                        (issue != MeetingIssue.Cancelled && history.isEmpty() && pinned == null)) ->
                        ErrorState(stringResource(issue.resource), viewState.isOnline, { eventHandler(MeetingViewEvent.OnRefresh) })
                    issue != null -> MeetingIssueText(issue)
                    pinned == null && history.isEmpty() -> MeetingBody(stringResource(Res.string.meetings_empty))
                    !viewState.hasMore && history.isEmpty() -> MeetingBody(stringResource(Res.string.meetings_history_empty))
                }
            }
            if (viewState.hasMore && !viewState.isLoading) {
                item(key = "meeting-history-more") {
                    SecondaryButton(
                        text = stringResource(Res.string.meetings_more),
                        onClick = { eventHandler(MeetingViewEvent.OnProposalsLoadMore) },
                        isEnabled = viewState.isOnline,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingVotersSheet(
    state: MeetingViewState,
    eventHandler: (MeetingViewEvent) -> Unit,
    avatarContent: @Composable (MeetingVote) -> Unit,
) {
    val spacing = RoyTheme.dimensions.spacing
    MeetingSheetFrame(
        title = stringResource(Res.string.meetings_voters),
        onDismiss = { eventHandler(MeetingViewEvent.OnDismiss) },
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            itemsIndexed(state.voters, key = { index, voter -> voter.userId ?: "${voter.createdAt}/$index" }) { _, voter ->
                RoyCard(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        avatarContent(voter)
                        MeetingBody(if (voter.userId == null) stringResource(Res.string.chat_author_deleted) else voter.displayName)
                    }
                }
            }
            item(key = "voter-footer") {
                val issue = state.votersIssue
                when {
                    state.isLoadingVoters -> LoadingState(LoadingMode.Inline, stringResource(Res.string.meetings_voters_loading))
                    issue != null -> ErrorState(
                        safeMessage = stringResource(issue.resource),
                        isRetryEnabled = state.isOnline,
                        onRetry = { eventHandler(MeetingViewEvent.OnVotersRetry) },
                    )
                    state.voters.isEmpty() -> MeetingBody(stringResource(Res.string.meetings_voters_empty))
                    state.hasMoreVoters -> SecondaryButton(
                        text = stringResource(Res.string.meetings_voters_more),
                        onClick = { eventHandler(MeetingViewEvent.OnVotersLoadMore) },
                        isEnabled = state.isOnline,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    issue: MeetingIssue?,
    isDestructive: Boolean,
    isEnabled: Boolean,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    MeetingSheetFrame(
        title = title,
        onDismiss = onDismiss,
        isDismissEnabled = !isSubmitting,
        dismissLabel = stringResource(Res.string.point_detail_cancel),
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.small),
        ) {
            MeetingBody(body)
            issue?.let { MeetingIssueText(it) }
            if (isDestructive) {
                DestructiveButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), isEnabled, isSubmitting)
            } else {
                PrimaryButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), isEnabled, isSubmitting)
            }
        }
    }
}

@Composable
private fun MeetingOverlapDialog(
    proposalId: String,
    conflicts: List<MeetingOverlap>,
    state: MeetingViewState,
    eventHandler: (MeetingViewEvent) -> Unit,
) {
    val spacing = RoyTheme.dimensions.spacing
    val isSubmitting = state.busyProposalId != null
    MeetingSheetFrame(
        title = stringResource(Res.string.meetings_overlap_title),
        onDismiss = { eventHandler(MeetingViewEvent.OnDismiss) },
        isDismissEnabled = !isSubmitting,
        dismissLabel = stringResource(Res.string.point_detail_cancel),
    ) {
        MeetingBody(stringResource(Res.string.meetings_overlap_body))
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            items(conflicts, key = { it.bookingId }) { conflict ->
                RoyCard(Modifier.fillMaxWidth()) {
                    MeetingBody(meetingInterval(conflict.startsAt, conflict.endsAt))
                }
            }
        }
        state.issue?.let { MeetingIssueText(it) }
        PrimaryButton(
            text = stringResource(Res.string.meetings_overlap_continue),
            onClick = { eventHandler(MeetingViewEvent.OnOverlapConfirmed) },
            modifier = Modifier.fillMaxWidth(),
            isEnabled = state.isOnline && proposalId in state.bookableIds && !isSubmitting,
            isSubmitting = isSubmitting,
        )
    }
}

@Composable
private fun MeetingMissingSelection(state: MeetingViewState, onDismiss: () -> Unit) {
    MeetingSheetFrame(title = stringResource(Res.string.meetings_detail), onDismiss = onDismiss) {
        val issue = state.issue
        if (issue != null) {
            MeetingIssueText(issue)
        } else {
            LoadingState(LoadingMode.Inline, stringResource(Res.string.meetings_loading))
        }
    }
}

@Composable
private fun MeetingSheetFrame(
    title: String,
    onDismiss: () -> Unit,
    isDismissEnabled: Boolean = true,
    dismissLabel: String = stringResource(Res.string.protected_back),
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimensions = RoyTheme.dimensions
    Dialog(
        onDismissRequest = { if (isDismissEnabled) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = isDismissEnabled,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .background(
                        RoyTheme.colors.surfaceElevated,
                        RoundedCornerShape(topStart = dimensions.radii.dialog, topEnd = dimensions.radii.dialog),
                    )
                    .padding(dimensions.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
            ) {
                MeetingHeading(title)
                content()
                TertiaryButton(label = dismissLabel, onClick = onDismiss, isEnabled = isDismissEnabled)
            }
        }
    }
}

@Composable
private fun MeetingHeading(text: String) {
    BasicText(text, modifier = Modifier.semantics { heading() },
        style = RoyTheme.typography.headingSmall.copy(color = RoyTheme.colors.textPrimary))
}

@Composable
private fun MeetingBody(text: String) {
    BasicText(text, style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textPrimary))
}
