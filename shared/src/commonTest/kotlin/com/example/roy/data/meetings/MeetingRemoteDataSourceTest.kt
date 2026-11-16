package com.example.roy.data.meetings

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.meetings.MeetingProposal
import com.example.roy.domain.meetings.MeetingRejection
import com.example.roy.domain.meetings.MeetingReply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class MeetingRemoteDataSourceTest {
    @Test
    fun overlapPreflightPreservesServerOrderAndConfirmationUsesSameKey() = runTest {
        val transport = FakeMeetingTransport(AuthHttpResponse(409, errorBody("OVERLAP_CONFIRM_REQUIRED", """
            {"overlaps":[{
                "bookingId":"$BOOKING","proposalId":"$OTHER_PROPOSAL","bookingOrder":14,
                "startsAt":"2030-06-01T12:00:00Z","endsAt":"2030-06-01T13:00:00Z",
                "bookedAt":"2030-05-31T12:00:00Z"
            }]}
        """)))
        val repository = MeetingRemoteDataSource(ORIGIN, transport)

        val conflict = assertIs<AuthResult.Success<MeetingReply<MeetingProposal>>>(
            repository.book(PROPOSAL, false, 8, KEY, "test-token"),
        )
        val overlap = assertIs<MeetingReply.OverlapRequired>(conflict.value).overlaps.single()
        assertEquals(OTHER_PROPOSAL, overlap.proposalId)
        assertEquals(14L, overlap.bookingOrder)
        assertEquals("2030-06-01T12:00:00Z", overlap.startsAt.toString())
        val initial = transport.requests.single()
        assertEquals(AuthHttpMethod.Post, initial.method)
        assertEquals("$ORIGIN/api/v1/proposals/$PROPOSAL/booking", initial.url)
        assertEquals(KEY, initial.headers["Idempotency-Key"])
        assertEquals("test-token", initial.accessToken)
        assertEquals(
            Json.parseToJsonElement("""{"confirmOverlap":false,"expectedRevision":8}"""),
            Json.parseToJsonElement(requireNotNull(initial.body)),
        )

        transport.response = AuthHttpResponse(409, errorBody("STALE_STATE"))
        val confirmed = assertIs<AuthResult.Success<MeetingReply<MeetingProposal>>>(
            repository.book(PROPOSAL, true, 8, KEY, "test-token"),
        )
        assertEquals(MeetingReply.Rejected(MeetingRejection.Stale), confirmed.value)
        val confirmation = transport.requests.last()
        assertEquals(initial.headers, confirmation.headers)
        assertEquals(
            Json.parseToJsonElement("""{"confirmOverlap":true,"expectedRevision":8}"""),
            Json.parseToJsonElement(requireNotNull(confirmation.body)),
        )
    }

    @Test
    fun voteUsesAbsoluteBooleanAndDecodesViewerProjection() = runTest {
        val transport = FakeMeetingTransport(AuthHttpResponse(200, proposalBody()))
        val repository = MeetingRemoteDataSource(ORIGIN, transport)
        val response = assertIs<AuthResult.Success<MeetingReply<MeetingProposal>>>(
            repository.vote(PROPOSAL, false, "test-token"),
        )
        val proposal = assertIs<MeetingReply.Data<MeetingProposal>>(response.value).value
        assertFalse(proposal.viewerGoing)
        assertEquals(2, proposal.goingCount)
        assertEquals("ready_to_book", proposal.status)
        val request = transport.requests.single()
        assertEquals(AuthHttpMethod.Put, request.method)
        assertEquals("$ORIGIN/api/v1/proposals/$PROPOSAL/vote", request.url)
        assertTrue(request.headers.isEmpty())
        assertEquals(
            Json.parseToJsonElement("""{"going":false}"""),
            Json.parseToJsonElement(requireNotNull(request.body)),
        )
    }

    @Test
    fun listEncodesCursorAndRejectsProposalsFromDifferentChat() = runTest {
        val transport = FakeMeetingTransport(AuthHttpResponse(200, """{"items":[${proposalBody()}],"nextCursor":null}"""))
        val repository = MeetingRemoteDataSource(ORIGIN, transport)
        val result = assertIs<AuthResult.Failure>(repository.list(OTHER_CHAT, "test-token", "A&limit=1"))
        assertEquals(AuthErrorCode.UnexpectedResponse, result.error.code)
        assertEquals(
            "$ORIGIN/api/v1/chats/$OTHER_CHAT/proposals?limit=30&cursor=A%26limit%3D1",
            transport.requests.single().url,
        )

        transport.response = AuthHttpResponse(403, errorBody("NOT_MEMBER"))
        assertEquals(
            MeetingReply.Rejected(MeetingRejection.NotMember),
            assertIs<AuthResult.Success<*>>(repository.list(CHAT, "test-token")).value,
        )
    }
}

private class FakeMeetingTransport(var response: AuthHttpResponse) : AuthHttpTransport {
    val requests = mutableListOf<AuthHttpRequest>()

    override suspend fun execute(request: AuthHttpRequest): AuthHttpResponse {
        requests += request
        return response
    }
}

private fun errorBody(code: String, details: String = "null") = """{"error":{"code":"$code","details":$details}}"""

private fun proposalBody() = """
    {"id":"$PROPOSAL","chatId":"$CHAT","proposedByUserId":null,
     "startsAt":"2030-06-01T12:00:00Z","endsAt":"2030-06-01T13:00:00Z",
     "status":"ready_to_book","goingCount":2,"viewerGoing":false,"booking":null,
     "revision":9,"createdAt":"2030-05-31T10:00:00Z","updatedAt":"2030-05-31T12:00:00Z"}
"""

private const val ORIGIN = "http://127.0.0.1:18080"
private const val CHAT = "00000000-0000-4000-8000-000000000001"
private const val OTHER_CHAT = "00000000-0000-4000-8000-000000000002"
private const val PROPOSAL = "00000000-0000-4000-8000-000000000003"
private const val OTHER_PROPOSAL = "00000000-0000-4000-8000-000000000004"
private const val BOOKING = "00000000-0000-4000-8000-000000000005"
private const val KEY = "00000000-0000-4000-8000-000000000006"
