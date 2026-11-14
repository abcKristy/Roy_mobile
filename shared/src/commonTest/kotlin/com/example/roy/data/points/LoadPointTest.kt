package com.example.roy.data.points

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadPointTest {
    private val origin = "http://127.0.0.1:18080"
    private val id = "11111111-1111-4111-8111-111111111111"

    @Test
    fun publicDetailReturnsResourceGoneForTheCanonical404() = runTest {
        val transport = AuthHttpTransport { request ->
            assertEquals(AuthHttpMethod.Get, request.method)
            assertEquals("$origin/api/v1/points/$id", request.url)
            assertNull(request.body)
            assertNull(request.accessToken)
            AuthHttpResponse(404, """{"error":{"code":"RESOURCE_GONE"}}""")
        }

        assertEquals(PointResult.Failure(PointError.ResourceGone), LoadPoint(PointRemoteDataSource(origin, transport))(id))
    }

    @Test
    fun unrelatedHttp404IsAnApiFailure() = runTest {
        val transport = AuthHttpTransport { AuthHttpResponse(404, """{"error":{"code":"NOT_FOUND"}}""") }

        assertEquals(
            PointResult.Failure(PointError.UnexpectedResponse),
            LoadPoint(PointRemoteDataSource(origin, transport))(id),
        )
    }
}
