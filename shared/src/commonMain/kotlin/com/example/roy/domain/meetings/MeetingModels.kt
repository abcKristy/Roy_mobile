package com.example.roy.domain.meetings

import kotlin.time.Instant

internal data class MeetingProposal(
    val id: String,
    val chatId: String,
    val proposedByUserId: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: String,
    val goingCount: Int,
    val viewerGoing: Boolean,
    val booking: MeetingBooking?,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

internal data class MeetingBooking(
    val id: String,
    val proposalId: String,
    val bookedByUserId: String?,
    val status: String,
    val bookingOrder: Long,
    val overlappingActive: List<MeetingOverlap>,
    val bookedAt: Instant,
    val cancelledAt: Instant?,
    val cancelReason: String?,
)

internal data class MeetingOverlap(
    val bookingId: String,
    val proposalId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val bookedAt: Instant,
    val bookingOrder: Long,
)

internal data class MeetingVote(
    val proposalId: String,
    val userId: String?,
    val displayName: String,
    val avatarUrl: String?,
    val createdAt: Instant,
)

internal data class MeetingPage<T>(val items: List<T>, val nextCursor: String?)
