package com.example.roy.data.points

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.LoadViewport
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.Position
import com.example.roy.domain.map.ViewportQuery
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ViewportLoaderTest {
    private val bbox = BoundingBox(west = 37.5, south = 55.6, east = 37.8, north = 55.9)
    private val origin = "http://127.0.0.1:18080"
    private val firstId = "11111111-1111-4111-8111-111111111111"
    private val secondId = "22222222-2222-4222-8222-222222222222"

    @Test
    fun loadsAllPagesWithUnchangedQueryAndDistinctPublicPoints() = runTest {
        val transport = RecordingTransport(
            page(listOf(point(firstId)), "opaque +/%"),
            page(listOf(point(firstId), point(secondId)), null),
        )
        val query = ViewportQuery(bbox, PointFilter("other", "A&B", peopleMin = 1, peopleMax = 3))

        val snapshot = assertIs<PointResult.Success<List<Point>>>(loader(transport)(query)).value
        assertEquals(listOf(firstId, secondId), snapshot.map { it.id })
        val first = snapshot.first()
        assertEquals(Position(latitude = 55.7512, longitude = 37.6184), first.position)
        assertEquals(4, first.peopleCount)
        assertEquals("Мяч", first.topics.single().label)
        assertEquals(2, first.topics.single().participantCount)

        val expected = "$origin/api/v1/points?west=37.5&south=55.6&east=37.8&north=55.9" +
            "&topicKey=other&customTopicLabel=A%26B&peopleMin=1&peopleMax=3&limit=30"
        assertEquals(listOf(expected, "$expected&cursor=opaque%20%2B%2F%25"), transport.requests.map { it.url })
        transport.requests.forEach {
            assertEquals(AuthHttpMethod.Get, it.method)
            assertNull(it.accessToken)
            assertNull(it.body)
        }
    }

    @Test
    fun secondPageFailureDiscardsPartialSnapshotAndRetryStartsWithoutCursor() = runTest {
        val transport = RecordingTransport(
            page(listOf(point(firstId)), "next"),
            AuthHttpResponse(503, """{"error":{"code":"SERVICE_UNAVAILABLE"}}"""),
            page(listOf(point(secondId)), null),
        )
        val load = loader(transport)
        val query = ViewportQuery(bbox)

        assertEquals(PointResult.Failure(PointError.ServiceUnavailable), load(query))
        val points = assertIs<PointResult.Success<List<Point>>>(load(query)).value
        assertEquals(secondId, points.single().id)
        assertEquals(transport.requests.first().url, transport.requests.last().url)
    }

    @Test
    fun repeatedCursorFailsInsteadOfFetchingTheSamePageAgain() = runTest {
        val transport = RecordingTransport(
            page(listOf(point(firstId)), "next"),
            page(listOf(point(secondId)), "next"),
        )

        assertEquals(PointResult.Failure(PointError.UnexpectedResponse), loader(transport)(ViewportQuery(bbox)))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun missingCursorFieldCannotBeAcceptedAsACompleteSnapshot() = runTest {
        val transport = RecordingTransport(AuthHttpResponse(200, """{"items":[]}"""))

        assertEquals(PointResult.Failure(PointError.UnexpectedResponse), loader(transport)(ViewportQuery(bbox)))
    }

    private fun loader(transport: AuthHttpTransport) = LoadViewport(PointRemoteDataSource(origin, transport))

    private class RecordingTransport(vararg responses: AuthHttpResponse?) : AuthHttpTransport {
        private val pages = responses.toList()
        val requests = mutableListOf<AuthHttpRequest>()

        override suspend fun execute(request: AuthHttpRequest): AuthHttpResponse? {
            requests += request
            return pages[requests.lastIndex]
        }
    }

    private fun page(points: List<String>, cursor: String?): AuthHttpResponse {
        val next = cursor?.let { "\"$it\"" } ?: "null"
        return AuthHttpResponse(200, """{"items":[${points.joinToString(",")}],"nextCursor":$next}""")
    }

    private fun point(id: String): String = """
        {
          "id":"$id",
          "position":{"lat":55.7512,"lon":37.6184},
          "address":"Москва, улица Примерная, 4",
          "addressSource":"manual",
          "peopleCount":4,
          "topics":[{"id":"33333333-3333-4333-8333-333333333333","presetKey":"football","label":"Мяч",
                     "participantCount":2,"chatId":"44444444-4444-4444-8444-444444444444"}],
          "createdAt":"2026-11-04T12:00:00Z",
          "updatedAt":"2026-11-04T12:00:00Z"
        }
    """.trimIndent()
}
