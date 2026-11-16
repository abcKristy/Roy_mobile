package com.example.roy.data.meetings

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MeetingAvatarAdapterTest {
    @Test
    fun protectedMissingAvatarFallsBackWithoutRepeatingRequest() = runTest {
        val (session, scopeId) = activeSession()
        val observed = mutableListOf<AuthHttpRequest>()
        val loader = MeetingAvatarAdapter(ORIGIN, AuthHttpTransport { request ->
            observed += request
            AuthHttpResponse(404, "")
        }, session).forScope(scopeId)

        assertNull(loader.load(USER, AVATAR))
        val request = observed.single()
        assertEquals(AuthHttpMethod.Get, request.method)
        assertEquals(ORIGIN + AVATAR, request.url)
        assertEquals("fixture-access", request.accessToken)
        assertEquals("image/jpeg, image/png", request.headers["Accept"])
        assertTrue(request.isBinaryResponseExpected)
    }

    @Test
    fun mismatchedPathsAndExpiredScopeNeverReachTransport() = runTest {
        val (session, scopeId) = activeSession()
        val transport = AuthHttpTransport { error("An invalid avatar request reached the transport") }
        val adapter = MeetingAvatarAdapter(ORIGIN, transport, session)
        val loader = adapter.forScope(scopeId)

        assertNull(loader.load(USER, "https://another.example.test$AVATAR"))
        assertNull(loader.load(USER, "$AVATAR?redirect=https://another.example.test"))
        assertNull(loader.load(USER, "/api/v1/users/eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee/avatar"))
        assertNull(adapter.forScope(scopeId + 1).load(USER, AVATAR))
    }

    @Test
    fun acceptsOnlyBoundedServerImageResponses() = runTest {
        val (session, scopeId) = activeSession()
        val expected = byteArrayOf(1, 2, 3)
        var response = AuthHttpResponse(200, "", binaryBody = expected, contentType = "image/png")
        val loader = MeetingAvatarAdapter(ORIGIN, AuthHttpTransport { response }, session).forScope(scopeId)

        assertContentEquals(expected, loader.load(USER, AVATAR))
        response = AuthHttpResponse(200, "", binaryBody = expected, contentType = "image/svg+xml")
        assertNull(loader.load(USER, AVATAR))
        response = AuthHttpResponse(200, "", binaryBody = ByteArray(5 * 1024 * 1024 + 1), contentType = "image/png")
        assertNull(loader.load(USER, AVATAR))
    }

    private suspend fun activeSession(): Pair<SessionCoordinator, Long> {
        val repository = SessionTestRepository()
        val store = SessionTestStore().apply { saved = repository.first }
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        return coordinator to assertIs<SessionStatus.Active>(coordinator.state.value).scopeId
    }

    private companion object {
        const val USER = "11111111-2222-3333-4444-555555555555"
        const val AVATAR = "/api/v1/users/$USER/avatar"
        const val ORIGIN = "http://127.0.0.1:8083"
    }
}
