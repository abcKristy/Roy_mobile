package com.example.roy.data.meetings

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthJson
import com.example.roy.data.network.encodeQueryValue
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.meetings.MeetingPage
import com.example.roy.domain.meetings.MeetingProposal
import com.example.roy.domain.meetings.MeetingRejection
import com.example.roy.domain.meetings.MeetingReply
import com.example.roy.domain.meetings.MeetingRepository
import com.example.roy.domain.meetings.MeetingVote
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Instant

internal class MeetingRemoteDataSource(
    baseUrl: String,
    private val transport: AuthHttpTransport,
) : MeetingRepository {
    private val origin = baseUrl.trimEnd('/')

    override suspend fun list(
        chatId: String,
        token: String,
        cursor: String?,
    ): AuthResult<MeetingReply<MeetingPage<MeetingProposal>>> =
        request(chatId, "chats/$chatId/proposals${pageQuery(cursor)}", token) { body ->
            val page = AuthJson.decodeFromString<MeetingPageDto<MeetingProposalDto>>(body)
            require(page.items.all { it.chatId == chatId })
            require(page.nextCursor == null || page.nextCursor.isNotEmpty() && page.nextCursor != cursor)
            MeetingPage(page.items.map(MeetingProposalDto::toDomain), page.nextCursor)
        }

    override suspend fun detail(proposalId: String, token: String): AuthResult<MeetingReply<MeetingProposal>> =
        request(proposalId, "proposals/$proposalId", token) { body -> proposal(body, proposalId = proposalId) }

    override suspend fun votes(
        proposalId: String,
        token: String,
        cursor: String?,
    ): AuthResult<MeetingReply<MeetingPage<MeetingVote>>> =
        request(proposalId, "proposals/$proposalId/votes${pageQuery(cursor)}", token) { body ->
            val page = AuthJson.decodeFromString<MeetingPageDto<MeetingVoteDto>>(body)
            require(page.items.all { it.proposalId == proposalId })
            require(page.nextCursor == null || page.nextCursor.isNotEmpty() && page.nextCursor != cursor)
            MeetingPage(page.items.map(MeetingVoteDto::toDomain), page.nextCursor)
        }

    override suspend fun create(
        chatId: String,
        startsAt: Instant,
        endsAt: Instant,
        key: String,
        token: String,
    ): AuthResult<MeetingReply<MeetingProposal>> = request(
        chatId, "chats/$chatId/proposals", token, AuthHttpMethod.Post, 201,
        AuthJson.encodeToString(MeetingCreateDto(startsAt.toString(), endsAt.toString())), key,
    ) { body -> proposal(body, chatId = chatId) }

    override suspend fun vote(
        proposalId: String,
        going: Boolean,
        token: String,
    ): AuthResult<MeetingReply<MeetingProposal>> = request(
        proposalId, "proposals/$proposalId/vote", token, AuthHttpMethod.Put,
        body = AuthJson.encodeToString(MeetingVoteCommandDto(going)),
    ) { body -> proposal(body, proposalId = proposalId) }

    override suspend fun book(
        proposalId: String,
        confirmOverlap: Boolean,
        expectedRevision: Long,
        key: String,
        token: String,
    ): AuthResult<MeetingReply<MeetingProposal>> = request(
        proposalId, "proposals/$proposalId/booking", token, AuthHttpMethod.Post, 201,
        AuthJson.encodeToString(MeetingBookDto(confirmOverlap, expectedRevision)), key,
    ) { body -> proposal(body, proposalId = proposalId) }

    override suspend fun close(
        proposalId: String,
        expectedRevision: Long,
        key: String,
        token: String,
    ): AuthResult<MeetingReply<MeetingProposal>> = request(
        proposalId, "proposals/$proposalId/close", token, AuthHttpMethod.Post,
        body = AuthJson.encodeToString(MeetingCloseDto(expectedRevision)), key = key,
    ) { body -> proposal(body, proposalId = proposalId) }

    private suspend fun <T> request(
        resourceId: String,
        path: String,
        token: String,
        method: AuthHttpMethod = AuthHttpMethod.Get,
        successStatus: Int = 200,
        body: String? = null,
        key: String? = null,
        decode: (String) -> T,
    ): AuthResult<MeetingReply<T>> {
        if (!MEETING_ID.matches(resourceId) || (key != null && !MEETING_ID.matches(key))) {
            return failure(AuthErrorCode.UnexpectedResponse)
        }
        val response = transport.execute(AuthHttpRequest(
            method, "$origin/api/v1/$path", body, token,
            maxResponseBytes = 2 * 1024 * 1024,
            headers = key?.let { mapOf("Idempotency-Key" to it) }.orEmpty(),
        )) ?: return failure(AuthErrorCode.Network)
        if (response.status == successStatus) {
            val value = parse { decode(response.body) } ?: return failure(AuthErrorCode.UnexpectedResponse)
            return AuthResult.Success(MeetingReply.Data(value))
        }
        return response.toIssue()
    }
}

private fun proposal(body: String, proposalId: String? = null, chatId: String? = null): MeetingProposal {
    val dto = AuthJson.decodeFromString<MeetingProposalDto>(body)
    require(proposalId == null || dto.id == proposalId)
    require(chatId == null || dto.chatId == chatId)
    return dto.toDomain()
}

private fun pageQuery(cursor: String?): String =
    "?limit=30" + cursor?.let { "&cursor=${it.encodeQueryValue()}" }.orEmpty()

private fun AuthHttpResponse.toIssue(): AuthResult<MeetingReply<Nothing>> {
    val error = parse { AuthJson.decodeFromString<MeetingErrorEnvelopeDto>(body).error }
    if (status == 401) {
        return failure(when (error?.code) {
            "AUTH_REQUIRED" -> AuthErrorCode.AuthRequired
            "SESSION_EXPIRED" -> AuthErrorCode.SessionExpired
            else -> AuthErrorCode.UnexpectedResponse
        })
    }
    if (status in 500..599) return failure(AuthErrorCode.ServiceUnavailable)
    if (status == 409 && error?.code == "OVERLAP_CONFIRM_REQUIRED") {
        val overlaps = error.details?.get("overlaps")?.let { values ->
            parse { AuthJson.decodeFromJsonElement<List<MeetingOverlapDto>>(values).map(MeetingOverlapDto::toDomain) }
        }?.takeIf { it.isNotEmpty() } ?: return failure(AuthErrorCode.UnexpectedResponse)
        return AuthResult.Success(MeetingReply.OverlapRequired(overlaps))
    }
    val reason = when (status to error?.code) {
        403 to "NOT_MEMBER" -> MeetingRejection.NotMember
        403 to "FORBIDDEN" -> MeetingRejection.Forbidden
        404 to "RESOURCE_GONE" -> MeetingRejection.Gone
        400 to "INVALID_CURSOR" -> MeetingRejection.InvalidCursor
        400 to "INVALID_FIELD", 422 to "INVALID_FIELD" -> MeetingRejection.InvalidField
        409 to "IDEMPOTENCY_KEY_REUSED" -> MeetingRejection.KeyReused
        409 to "STALE_STATE" -> MeetingRejection.Stale
        409 to "MEETING_ALREADY_STARTED" -> MeetingRejection.Started
        409 to "VOTE_REQUIRED" -> MeetingRejection.VoteRequired
        409 to "INSUFFICIENT_VOTES" -> MeetingRejection.InsufficientVotes
        409 to "PROPOSAL_CLOSED" -> MeetingRejection.Closed
        409 to "ALREADY_BOOKED" -> MeetingRejection.AlreadyBooked
        else -> return failure(AuthErrorCode.UnexpectedResponse)
    }
    return AuthResult.Success(MeetingReply.Rejected(reason))
}

private inline fun <T> parse(decode: () -> T): T? = try {
    decode()
} catch (_: IllegalArgumentException) {
    null
}

private fun failure(code: AuthErrorCode) = AuthResult.Failure(AuthError(code))
