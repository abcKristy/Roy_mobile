package com.example.roy.presentation.meetings

import com.example.roy.domain.meetings.MeetingOverlap
import com.example.roy.domain.meetings.MeetingProposal
import com.example.roy.domain.meetings.MeetingVote

internal data class MeetingViewState(
    val proposals: List<MeetingProposal> = emptyList(),
    val currentProposal: MeetingProposal? = null,
    val selectedProposal: MeetingProposal? = null,
    val viewerId: String? = null,
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val issue: MeetingIssue? = null,
    val sheet: MeetingSheet? = null,
    val voters: List<MeetingVote> = emptyList(),
    val isLoadingVoters: Boolean = false,
    val hasMoreVoters: Boolean = false,
    val votersIssue: MeetingIssue? = null,
    val draft: MeetingDraft = MeetingDraft(),
    val busyProposalId: String? = null,
    val isCreating: Boolean = false,
    val canCreate: Boolean = false,
    val votableIds: Set<String> = emptySet(),
    val bookableIds: Set<String> = emptySet(),
    val closableIds: Set<String> = emptySet(),
)

internal data class MeetingDraft(
    val date: String = "",
    val time: String = "",
    val durationMinutes: Int? = 60,
    val customMinutes: String = "",
    val dateIssue: MeetingIssue? = null,
    val timeIssue: MeetingIssue? = null,
    val durationIssue: MeetingIssue? = null,
)

internal sealed interface MeetingSheet {
    val proposalId: String? get() = null

    data object List : MeetingSheet
    data object Create : MeetingSheet
    data class Detail(override val proposalId: String) : MeetingSheet
    data class Voters(override val proposalId: String) : MeetingSheet
    data class Booking(override val proposalId: String) : MeetingSheet
    data class Overlap(override val proposalId: String, val conflicts: kotlin.collections.List<MeetingOverlap>) : MeetingSheet
    data class Close(override val proposalId: String) : MeetingSheet
}

internal enum class MeetingIssue {
    Offline, Unavailable, NotMember, Gone, Forbidden, InvalidField, InvalidCursor, KeyReused, Stale,
    Started, VoteRequired, InsufficientVotes, Closed, AlreadyBooked, Required, InvalidDate, InvalidTime,
    InvalidDuration, Cancelled,
}
