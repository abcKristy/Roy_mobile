package com.example.roy.data.notifications

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.push.PushAuthorization
import com.example.roy.push.PushRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationTest {
    @Test
    fun registersOnlyAllowedInstallationRotatesAndUnbindsBeforeLogout() = runTest {
        val auth = SessionTestRepository().apply { revoke = { AuthResult.Success(Unit) } }
        val session = SessionCoordinator(auth, SessionTestStore().apply { saved = auth.first })
        session.restore()
        val scope = (session.state.value as SessionStatus.Active).scopeId
        val requests = mutableListOf<AuthHttpRequest>()
        val transport = AuthHttpTransport { request ->
            requests += request
            AuthHttpResponse(if (request.method == AuthHttpMethod.Delete) 204 else 200, "")
        }
        val runtime = PushRuntime("04bc8bc5-f271-47f2-9035-be434691a450", "android") {}
        val registration = PushRegistration("https://example.invalid", transport, session, runtime)
        val connectivity = MutableStateFlow(true)
        backgroundScope.launch { registration.observe(connectivity) }
        runtime.updateToken("synthetic-start")
        runCurrent()
        assertTrue(requests.isEmpty())

        runtime.updateAuthorization(PushAuthorization.Allowed)
        runCurrent()
        runtime.updateToken("synthetic-after-rotation")
        runCurrent()
        registration.logout(scope)
        runtime.updateToken("synthetic-after-logout")
        runCurrent()

        assertEquals(listOf(AuthHttpMethod.Put, AuthHttpMethod.Put, AuthHttpMethod.Delete), requests.map { it.method })
        assertEquals(1, requests.map { it.url }.distinct().size)
        val payload = Json.parseToJsonElement(requireNotNull(requests[1].body)).jsonObject
        assertEquals("rustore", payload.getValue("provider").jsonPrimitive.content)
        assertEquals("synthetic-after-rotation", payload.getValue("token").jsonPrimitive.content)
    }

    @Test
    fun failedRevokeKeepsSessionSoLogoutCanBeRetried() = runTest {
        var logoutAttempts = 0
        val auth = SessionTestRepository().apply {
            revoke = {
                logoutAttempts++
                AuthResult.Success(Unit)
            }
        }
        val session = SessionCoordinator(auth, SessionTestStore().apply { saved = auth.first })
        session.restore()
        val active = session.state.value as SessionStatus.Active
        var attempts = 0
        val transport = AuthHttpTransport { request ->
            assertEquals(AuthHttpMethod.Delete, request.method)
            AuthHttpResponse(if (++attempts == 1) 503 else 204, "")
        }
        val runtime = PushRuntime("04bc8bc5-f271-47f2-9035-be434691a450", "android", initiallyRegistered = true) {}
        val registration = PushRegistration("https://example.invalid", transport, session, runtime)

        assertTrue(registration.logout(active.scopeId) is AuthResult.Failure)
        assertEquals(active, session.state.value)
        assertEquals(0, logoutAttempts)
        assertTrue(registration.logout(active.scopeId) is AuthResult.Success)
        assertEquals(SessionStatus.Guest, session.state.value)
        assertEquals(1, logoutAttempts)
        assertEquals(2, attempts)
    }
}
