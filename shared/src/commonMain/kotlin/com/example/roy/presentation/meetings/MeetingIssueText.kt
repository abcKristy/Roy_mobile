package com.example.roy.presentation.meetings

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.meetings_booking_cancelled
import roy.shared.generated.resources.meetings_issue_already_booked
import roy.shared.generated.resources.meetings_issue_closed
import roy.shared.generated.resources.meetings_issue_forbidden
import roy.shared.generated.resources.meetings_issue_gone
import roy.shared.generated.resources.meetings_issue_insufficient_votes
import roy.shared.generated.resources.meetings_issue_invalid_cursor
import roy.shared.generated.resources.meetings_issue_invalid_date
import roy.shared.generated.resources.meetings_issue_invalid_duration
import roy.shared.generated.resources.meetings_issue_invalid_field
import roy.shared.generated.resources.meetings_issue_invalid_time
import roy.shared.generated.resources.meetings_issue_key_reused
import roy.shared.generated.resources.meetings_issue_not_member
import roy.shared.generated.resources.meetings_issue_offline
import roy.shared.generated.resources.meetings_issue_required
import roy.shared.generated.resources.meetings_issue_stale
import roy.shared.generated.resources.meetings_issue_started
import roy.shared.generated.resources.meetings_issue_unavailable
import roy.shared.generated.resources.meetings_issue_vote_required

internal val MeetingIssue.resource: StringResource
    get() = when (this) {
        MeetingIssue.Offline -> Res.string.meetings_issue_offline
        MeetingIssue.Unavailable -> Res.string.meetings_issue_unavailable
        MeetingIssue.NotMember -> Res.string.meetings_issue_not_member
        MeetingIssue.Gone -> Res.string.meetings_issue_gone
        MeetingIssue.Forbidden -> Res.string.meetings_issue_forbidden
        MeetingIssue.InvalidField -> Res.string.meetings_issue_invalid_field
        MeetingIssue.InvalidCursor -> Res.string.meetings_issue_invalid_cursor
        MeetingIssue.KeyReused -> Res.string.meetings_issue_key_reused
        MeetingIssue.Stale -> Res.string.meetings_issue_stale
        MeetingIssue.Started -> Res.string.meetings_issue_started
        MeetingIssue.VoteRequired -> Res.string.meetings_issue_vote_required
        MeetingIssue.InsufficientVotes -> Res.string.meetings_issue_insufficient_votes
        MeetingIssue.Closed -> Res.string.meetings_issue_closed
        MeetingIssue.AlreadyBooked -> Res.string.meetings_issue_already_booked
        MeetingIssue.Required -> Res.string.meetings_issue_required
        MeetingIssue.InvalidDate -> Res.string.meetings_issue_invalid_date
        MeetingIssue.InvalidTime -> Res.string.meetings_issue_invalid_time
        MeetingIssue.InvalidDuration -> Res.string.meetings_issue_invalid_duration
        MeetingIssue.Cancelled -> Res.string.meetings_booking_cancelled
    }

@Composable
internal fun MeetingIssueText(issue: MeetingIssue) {
    val colors = RoyTheme.colors
    BasicText(
        text = stringResource(issue.resource),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = RoyTheme.typography.bodySmall.copy(
            color = if (issue == MeetingIssue.Cancelled) colors.textSecondary else colors.error,
        ),
    )
}
