package com.example.roy.presentation.meetings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.roy.domain.meetings.MeetingBooking
import com.example.roy.domain.meetings.MeetingProposal
import com.example.roy.presentation.components.DestructiveButton
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.core.formatLocalDateTime
import com.example.roy.presentation.theme.RoyTheme
import kotlin.time.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.meetings_book
import roy.shared.generated.resources.meetings_booking_active
import roy.shared.generated.resources.meetings_booking_cancelled
import roy.shared.generated.resources.meetings_booking_cancelled_at
import roy.shared.generated.resources.meetings_booking_number
import roy.shared.generated.resources.meetings_booking_yours
import roy.shared.generated.resources.meetings_cancel_closed_by_author
import roy.shared.generated.resources.meetings_cancel_insufficient_votes
import roy.shared.generated.resources.meetings_cancel_point_deleted
import roy.shared.generated.resources.meetings_close_proposal
import roy.shared.generated.resources.meetings_interval
import roy.shared.generated.resources.meetings_overlap_active
import roy.shared.generated.resources.meetings_overlap_other_priority
import roy.shared.generated.resources.meetings_overlap_own_priority
import roy.shared.generated.resources.meetings_pending
import roy.shared.generated.resources.meetings_pinned
import roy.shared.generated.resources.meetings_rebook
import roy.shared.generated.resources.meetings_status_booked
import roy.shared.generated.resources.meetings_status_closed
import roy.shared.generated.resources.meetings_status_ready
import roy.shared.generated.resources.meetings_status_unknown
import roy.shared.generated.resources.meetings_status_voting
import roy.shared.generated.resources.meetings_unvote
import roy.shared.generated.resources.meetings_vote
import roy.shared.generated.resources.meetings_vote_count
import roy.shared.generated.resources.meetings_voters

@Composable
internal fun PinnedMeeting(proposal: MeetingProposal, onOpen: () -> Unit) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    RoyCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        BasicText(
            text = stringResource(Res.string.meetings_pinned),
            style = typography.bodySmall.copy(color = colors.textSecondary),
        )
        BasicText(
            text = meetingInterval(proposal.startsAt, proposal.endsAt),
            style = typography.bodySmall.copy(color = colors.textPrimary),
        )
        BasicText(stringResource(Res.string.meetings_vote_count, proposal.goingCount),
            style = typography.caption.copy(color = colors.textSecondary))
    }
}

@Composable
internal fun MeetingProposalCard(
    proposal: MeetingProposal,
    state: MeetingViewState,
    eventHandler: (MeetingViewEvent) -> Unit,
) {
    val colors = RoyTheme.colors
    val type = RoyTheme.typography
    val spacing = RoyTheme.dimensions.spacing
    val isPending = state.busyProposalId == proposal.id
    val isActionAllowed = state.isOnline && state.busyProposalId == null
    RoyCard(Modifier.fillMaxWidth()) {
        BasicText(
            text = meetingInterval(proposal.startsAt, proposal.endsAt),
            modifier = Modifier.semantics { heading() },
            style = type.headingSmall.copy(color = colors.textPrimary),
        )
        BasicText(stringResource(proposal.status.resourceForStatus()), style = type.bodySmall.copy(color = colors.textSecondary))
        BasicText(
            text = stringResource(Res.string.meetings_vote_count, proposal.goingCount),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = type.body.copy(color = colors.textPrimary),
        )
        proposal.booking?.let { MeetingBookingDetails(it, state.viewerId) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            if (proposal.id in state.votableIds) {
                SecondaryButton(
                    text = stringResource(if (proposal.viewerGoing) Res.string.meetings_unvote else Res.string.meetings_vote),
                    onClick = { eventHandler(MeetingViewEvent.OnVote(proposal.id)) },
                    isEnabled = isActionAllowed,
                )
            }
            TertiaryButton(
                label = stringResource(Res.string.meetings_voters),
                onClick = { eventHandler(MeetingViewEvent.OnVotersRequested(proposal.id)) },
                isEnabled = isActionAllowed,
            )
        }
        if (isPending) {
            LoadingState(LoadingMode.Inline, stringResource(Res.string.meetings_pending))
        }
        if (proposal.id in state.bookableIds) {
            PrimaryButton(
                text = stringResource(
                    if (proposal.booking?.status == "cancelled") Res.string.meetings_rebook else Res.string.meetings_book,
                ),
                onClick = { eventHandler(MeetingViewEvent.OnBookRequested(proposal.id)) },
                modifier = Modifier.fillMaxWidth(),
                isEnabled = isActionAllowed,
            )
        }
        if (proposal.id in state.closableIds) {
            DestructiveButton(
                label = stringResource(Res.string.meetings_close_proposal),
                onClick = { eventHandler(MeetingViewEvent.OnCloseRequested(proposal.id)) },
                modifier = Modifier.fillMaxWidth(),
                isEnabled = isActionAllowed,
            )
        }
    }
}

@Composable
private fun MeetingBookingDetails(booking: MeetingBooking, viewerId: String?) {
    val colors = RoyTheme.colors
    val type = RoyTheme.typography
    val spacing = RoyTheme.dimensions.spacing
    Column(Modifier.padding(vertical = spacing.extraSmall), verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
        when (booking.status) {
            "active" -> {
                BasicText(stringResource(Res.string.meetings_booking_active), style = type.body.copy(color = colors.textPrimary))
                BasicText(stringResource(Res.string.meetings_booking_number, booking.bookingOrder),
                    style = type.bodySmall.copy(color = colors.textSecondary))
                if (viewerId != null && viewerId == booking.bookedByUserId) {
                    BasicText(stringResource(Res.string.meetings_booking_yours),
                        style = type.bodySmall.copy(color = colors.textSecondary))
                }
                if (booking.overlappingActive.isNotEmpty()) {
                    BasicText(stringResource(Res.string.meetings_overlap_active), style = type.body.copy(color = colors.textPrimary))
                    booking.overlappingActive.forEach { overlap ->
                        val priority = if (booking.bookingOrder < overlap.bookingOrder) {
                            Res.string.meetings_overlap_own_priority
                        } else {
                            Res.string.meetings_overlap_other_priority
                        }
                        BasicText(
                            stringResource(priority, meetingInterval(overlap.startsAt, overlap.endsAt)),
                            style = type.bodySmall.copy(color = colors.textSecondary),
                        )
                    }
                }
            }
            "cancelled" -> {
                BasicText(stringResource(Res.string.meetings_booking_cancelled), style = type.body.copy(color = colors.textPrimary))
                meetingCancelReasonResource(booking.cancelReason)?.let {
                    BasicText(stringResource(it), style = type.bodySmall.copy(color = colors.textSecondary))
                }
                booking.cancelledAt?.let {
                    BasicText(stringResource(Res.string.meetings_booking_cancelled_at, formatLocalDateTime(it)),
                        style = type.bodySmall.copy(color = colors.textSecondary))
                }
            }
        }
    }
}

@Composable
internal fun meetingInterval(start: Instant, end: Instant): String =
    stringResource(Res.string.meetings_interval, formatLocalDateTime(start), formatLocalDateTime(end))

internal fun meetingCancelReasonResource(reason: String?): StringResource? = when (reason) {
    "closed_by_author" -> Res.string.meetings_cancel_closed_by_author
    "insufficient_votes" -> Res.string.meetings_cancel_insufficient_votes
    "point_deleted" -> Res.string.meetings_cancel_point_deleted
    else -> null
}

private fun String.resourceForStatus(): StringResource = when (this) {
    "voting" -> Res.string.meetings_status_voting
    "ready_to_book" -> Res.string.meetings_status_ready
    "booked" -> Res.string.meetings_status_booked
    "closed" -> Res.string.meetings_status_closed
    else -> Res.string.meetings_status_unknown
}
