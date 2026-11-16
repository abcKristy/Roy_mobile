package com.example.roy.domain.meetings

import com.example.roy.domain.auth.AuthResult
import kotlin.time.Instant

internal interface MeetingRepository {
    suspend fun list(chatId: String, token: String, cursor: String? = null): AuthResult<MeetingReply<MeetingPage<MeetingProposal>>>
    suspend fun detail(proposalId: String, token: String): AuthResult<MeetingReply<MeetingProposal>>
    suspend fun votes(proposalId: String, token: String, cursor: String? = null): AuthResult<MeetingReply<MeetingPage<MeetingVote>>>

    suspend fun create(
        chatId: String,
        startsAt: Instant,
        endsAt: Instant,
        key: String,
        token: String,
    ): AuthResult<MeetingReply<MeetingProposal>>

    suspend fun vote(proposalId: String, going: Boolean, token: String): AuthResult<MeetingReply<MeetingProposal>>

    suspend fun book(
        proposalId: String,
        confirmOverlap: Boolean,
        expectedRevision: Long,
        key: String,
        token: String,
    ): AuthResult<MeetingReply<MeetingProposal>>

    suspend fun close(
        proposalId: String,
        expectedRevision: Long,
        key: String,
        token: String,
    ): AuthResult<MeetingReply<MeetingProposal>>
}

internal sealed interface MeetingReply<out T> {
    data class Data<T>(val value: T) : MeetingReply<T>
    data class Rejected(val reason: MeetingRejection) : MeetingReply<Nothing>
    data class OverlapRequired(val overlaps: List<MeetingOverlap>) : MeetingReply<Nothing>
}

internal enum class MeetingRejection {
    NotMember,
    Gone,
    Forbidden,
    InvalidField,
    InvalidCursor,
    KeyReused,
    Stale,
    Started,
    VoteRequired,
    InsufficientVotes,
    Closed,
    AlreadyBooked,
}
