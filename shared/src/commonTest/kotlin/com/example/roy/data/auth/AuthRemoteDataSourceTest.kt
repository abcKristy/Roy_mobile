package com.example.roy.data.auth

import com.example.roy.domain.auth.AuthCredentials
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthField
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthSession
import com.example.roy.domain.auth.PrivacyPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class AuthRemoteDataSourceTest {
    @Test
    fun loginUsesOnlyPhoneAndPasswordAndDecodesWholeSession() = runTest {
        val transport = FakeTransport(AuthHttpResponse(200, SESSION))
        val repository = repository(transport)
        val result = assertIs<AuthResult.Success<AuthSession>>(
            repository.login(AuthCredentials.Login("+12025550123", "redaction-password-marker"))
        )
        val request = transport.request
        assertEquals(AuthHttpMethod.Post, request.method)
        assertEquals("http://127.0.0.1:18080/api/v1/auth/login", request.url)
        assertEquals(setOf("phone", "password"), Json.parseToJsonElement(requireNotNull(request.body)).jsonObject.keys)
        assertNull(request.accessToken)
        assertEquals("test-access", result.value.accessToken)
        assertEquals("test-refresh", result.value.refreshToken)
        assertEquals("2026-11-14T13:00:00Z", result.value.refreshExpiresAt.toString())
        assertEquals("Test user", result.value.user.displayName)
        assertFalse(request.toString().contains("redaction-password-marker"))
        assertFalse(result.toString().contains("test-access"))
    }

    @Test
    fun distinguishesTransportFailureAndTerminalAuthResponses() = runTest {
        val transport = FakeTransport(AuthHttpResponse(401, failure("INVALID_CREDENTIALS")))
        val repository = repository(transport)
        val rejected = assertIs<AuthResult.Failure>(repository.login(AuthCredentials.Login("+12025550123", "redaction-password-marker")))
        assertEquals(AuthErrorCode.InvalidCredentials, rejected.error.code)

        transport.response = AuthHttpResponse(401, failure("SESSION_EXPIRED"))
        val expired = assertIs<AuthResult.Failure>(repository.refresh("old-refresh"))
        assertEquals(AuthErrorCode.SessionExpired, expired.error.code)
        assertNull(transport.request.accessToken)

        transport.response = null
        val network = assertIs<AuthResult.Failure>(repository.refresh("old-refresh"))
        assertEquals(AuthErrorCode.Network, network.error.code)

        transport.response = AuthHttpResponse(401, "<html>proxy</html>")
        val malformed = assertIs<AuthResult.Failure>(repository.refresh("old-refresh"))
        assertEquals(AuthErrorCode.UnexpectedResponse, malformed.error.code)
    }

    @Test
    fun registerSendsExactConsentAndMapsDuplicateAndStalePolicy() = runTest {
        val transport = FakeTransport(AuthHttpResponse(409, failure("DUPLICATE_PHONE")))
        val repository = repository(transport)
        val input = AuthCredentials.Register("+12025550123", "example!", "Test user", 18, "local.1")
        val duplicate = assertIs<AuthResult.Failure>(repository.register(input))
        assertEquals(AuthErrorCode.DuplicatePhone, duplicate.error.code)
        assertEquals(AuthField.Phone, duplicate.error.field)
        assertEquals(
            Json.parseToJsonElement(
                """{"phone":"+12025550123","password":"example!","displayName":"Test user","ageYears":18,"acceptedPrivacyPolicyVersion":"local.1"}"""
            ),
            Json.parseToJsonElement(requireNotNull(transport.request.body)),
        )

        transport.response = AuthHttpResponse(
            422, failure("PRIVACY_POLICY_ACCEPTANCE_REQUIRED", """{"currentVersion":"local.2"}"""),
        )
        val stale = assertIs<AuthResult.Failure>(repository.register(input))
        assertEquals(AuthErrorCode.PolicyAcceptanceRequired, stale.error.code)
        assertEquals("local.2", stale.error.currentVersion)
    }

    @Test
    fun policyRequiresPublishedMetadataAndHttpOriginRules() = runTest {
        val transport = FakeTransport(
            AuthHttpResponse(200, policyWith("http://127.0.0.1:18080/api/v1/legal/privacy/document"))
        )
        val repository = repository(transport)
        val local = assertIs<AuthResult.Success<PrivacyPolicy>>(repository.privacyPolicy())
        assertEquals("local.2", local.value.version)

        assertFailsWith<IllegalArgumentException> { AuthRemoteDataSource("http://127.0.0.1:0", transport) }
        assertFailsWith<IllegalArgumentException> { AuthRemoteDataSource("https://example.test:65536", transport) }
        transport.response = AuthHttpResponse(200, policyWith("https://example.test:99999/api/v1/legal/privacy/document"))
        assertEquals(
            AuthErrorCode.UnexpectedResponse,
            assertIs<AuthResult.Failure>(repository.privacyPolicy()).error.code,
        )

        transport.response = AuthHttpResponse(200, policyWith("intent://example"))
        assertEquals(
            AuthErrorCode.UnexpectedResponse,
            assertIs<AuthResult.Failure>(repository.privacyPolicy()).error.code,
        )

        transport.response = AuthHttpResponse(503, failure("SERVICE_UNAVAILABLE"))
        assertEquals(
            AuthErrorCode.ServiceUnavailable,
            assertIs<AuthResult.Failure>(repository.privacyPolicy()).error.code,
        )
    }

    @Test
    fun sessionCodecPreservesSnapshotAndRejectsMalformedTokens() {
        val decoded = AuthSessionCodec.decode(SESSION)
        val restored = AuthSessionCodec.decode(AuthSessionCodec.encode(decoded))
        assertEquals(decoded.accessToken, restored.accessToken)
        assertEquals(decoded.expiresAt, restored.expiresAt)
        assertEquals(decoded.refreshToken, restored.refreshToken)
        assertEquals(decoded.refreshExpiresAt, restored.refreshExpiresAt)
        assertEquals(decoded.user.id, restored.user.id)
        assertEquals(decoded.user.phone, restored.user.phone)
        assertEquals(decoded.user.ageYears, restored.user.ageYears)
        assertNull(restored.user.avatarUrl)
        assertFailsWith<IllegalArgumentException> {
            AuthSessionCodec.decode(SESSION.replace("test-refresh", ""))
        }
    }

    private class FakeTransport(var response: AuthHttpResponse?) : AuthHttpTransport {
        lateinit var request: AuthHttpRequest
        override suspend fun execute(request: AuthHttpRequest): AuthHttpResponse? {
            this.request = request
            return response
        }
    }

    private fun repository(transport: FakeTransport) = AuthRemoteDataSource("http://127.0.0.1:18080", transport)

    private fun failure(code: String, details: String = "null") =
        """{"error":{"code":"$code","message":"discarded","details":$details,"retryAfterSeconds":null},"requestId":"test"}"""

    private fun policyWith(url: String) =
        """{"version":"local.2","publishedAt":"2026-11-14T12:00:00Z","url":"$url"}"""

    private companion object {
        const val SESSION = """{"accessToken":"test-access","expiresAt":"2026-11-14T12:01:00Z","refreshToken":"test-refresh","refreshExpiresAt":"2026-11-14T13:00:00Z","user":{"id":"00000000-0000-4000-8000-000000000001","displayName":"Test user","avatarUrl":null,"phone":"+12025550123","ageYears":18,"messageMutedUntil":null,"createdAt":"2026-11-14T12:00:00Z"}}"""
    }
}
