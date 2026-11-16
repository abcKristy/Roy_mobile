package com.example.roy.data.meetings

import com.example.roy.domain.meetings.MeetingBooking
import com.example.roy.domain.meetings.MeetingOverlap
import com.example.roy.domain.meetings.MeetingProposal
import com.example.roy.domain.meetings.MeetingVote
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

@Serializable
internal data class MeetingProposalDto(
    val id: String,
    val chatId: String,
    val proposedByUserId: String?,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val goingCount: Int,
    val viewerGoing: Boolean,
    val booking: MeetingBookingDto?,
    val revision: Long,
    val createdAt: String,
    val updatedAt: String,
) {
    fun toDomain(): MeetingProposal {
        val start = Instant.parse(startsAt)
        val end = Instant.parse(endsAt)
        require(MEETING_ID.matches(id) && MEETING_ID.matches(chatId) && start < end)
        require(revision >= 0 && goingCount >= 0 && (!viewerGoing || goingCount > 0))
        require(booking == null || booking.proposalId == id)
        return MeetingProposal(
            id, chatId, proposedByUserId, start, end, status, goingCount, viewerGoing,
            booking?.toDomain(), revision, Instant.parse(createdAt), Instant.parse(updatedAt),
        )
    }
}

@Serializable
internal data class MeetingBookingDto(
    val id: String,
    val proposalId: String,
    val bookedByUserId: String?,
    val status: String,
    val bookingOrder: Long,
    val overlappingActive: List<MeetingOverlapDto>,
    val bookedAt: String,
    val cancelledAt: String?,
    val cancelReason: String?,
) {
    fun toDomain(): MeetingBooking {
        require(MEETING_ID.matches(id) && bookingOrder > 0)
        return MeetingBooking(
            id, proposalId, bookedByUserId, status, bookingOrder,
            overlappingActive.map(MeetingOverlapDto::toDomain),
            Instant.parse(bookedAt), cancelledAt?.let(Instant::parse), cancelReason,
        )
    }
}

@Serializable
internal data class MeetingOverlapDto(
    val bookingId: String,
    val proposalId: String,
    val startsAt: String,
    val endsAt: String,
    val bookedAt: String,
    val bookingOrder: Long,
) {
    fun toDomain(): MeetingOverlap {
        val start = Instant.parse(startsAt)
        val end = Instant.parse(endsAt)
        require(MEETING_ID.matches(bookingId) && MEETING_ID.matches(proposalId) && start < end && bookingOrder > 0)
        return MeetingOverlap(bookingId, proposalId, start, end, Instant.parse(bookedAt), bookingOrder)
    }
}

@Serializable
internal data class MeetingVoteDto(val proposalId: String, val user: MeetingVoterDto, val createdAt: String) {
    fun toDomain(): MeetingVote {
        require(user.id == null || MEETING_ID.matches(user.id))
        return MeetingVote(proposalId, user.id, user.displayName.orEmpty(), user.avatarUrl, Instant.parse(createdAt))
    }
}

@Serializable
internal data class MeetingVoterDto(val id: String?, val displayName: String?, val avatarUrl: String?)

@Serializable
internal data class MeetingPageDto<T>(val items: List<T>, val nextCursor: String?)

@Serializable
internal data class MeetingCreateDto(val startsAt: String, val endsAt: String)

@Serializable
internal data class MeetingVoteCommandDto(val going: Boolean)

@Serializable
internal data class MeetingBookDto(val confirmOverlap: Boolean, val expectedRevision: Long)

@Serializable
internal data class MeetingCloseDto(val expectedRevision: Long)

@Serializable
internal data class MeetingErrorEnvelopeDto(val error: MeetingErrorDto)

@Serializable
internal data class MeetingErrorDto(val code: String, val details: JsonObject? = null)

internal val MEETING_ID = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
