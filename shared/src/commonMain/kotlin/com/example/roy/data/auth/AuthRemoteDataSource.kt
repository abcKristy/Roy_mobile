package com.example.roy.data.auth

import com.example.roy.domain.auth.AuthCredentials
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthField
import com.example.roy.domain.auth.AuthRepository
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthSession
import com.example.roy.domain.auth.AuthUser
import com.example.roy.domain.auth.PrivacyPolicy
import kotlin.time.Instant
import kotlinx.serialization.json.JsonPrimitive

internal class AuthRemoteDataSource(
    baseUrl: String,
    private val transport: AuthHttpTransport,
) : AuthRepository {
    private val origin = baseUrl.trimEnd('/').also {
        require(it.isOrigin(SecureOrigin) || it.isOrigin(LocalOrigin)) { "Invalid auth API origin" }
    }

    override suspend fun login(credentials: AuthCredentials.Login): AuthResult<AuthSession> =
        postSession("auth/login", 200, AuthJson.encodeToString(LoginDto(credentials.phone, credentials.password)))

    override suspend fun register(credentials: AuthCredentials.Register): AuthResult<AuthSession> =
        postSession(
            "auth/register",
            201,
            with(credentials) {
                AuthJson.encodeToString(RegisterDto(phone, password, displayName, ageYears, acceptedPrivacyPolicyVersion))
            },
        )

    override suspend fun refresh(refreshToken: String): AuthResult<AuthSession> =
        postSession("auth/refresh", 200, AuthJson.encodeToString(RefreshDto(refreshToken)))

    override suspend fun currentUser(accessToken: String): AuthResult<AuthUser> =
        request(AuthHttpMethod.Get, "me", 200, accessToken = accessToken) {
            AuthJson.decodeFromString<UserDto>(it).toDomain()
        }

    override suspend fun logout(accessToken: String): AuthResult<Unit> =
        request(AuthHttpMethod.Post, "auth/logout", 204, accessToken = accessToken) {}

    override suspend fun privacyPolicy(): AuthResult<PrivacyPolicy> =
        request(AuthHttpMethod.Get, "legal/privacy", 200) { body ->
            val document = AuthJson.decodeFromString<PrivacyPolicyDto>(body)
            require(document.version.isNotBlank() && isPolicyDocumentUrl(document.url))
            PrivacyPolicy(document.version, Instant.parse(document.publishedAt), document.url)
        }

    private suspend fun postSession(path: String, status: Int, body: String): AuthResult<AuthSession> =
        request(AuthHttpMethod.Post, path, status, body = body, decode = AuthSessionCodec::decode)

    private suspend fun <T> request(
        method: AuthHttpMethod,
        path: String,
        successStatus: Int,
        body: String? = null,
        accessToken: String? = null,
        decode: (String) -> T,
    ): AuthResult<T> {
        val response = transport.execute(AuthHttpRequest(method, "$origin/api/v1/$path", body, accessToken))
            ?: return AuthResult.Failure(AuthError(AuthErrorCode.Network))
        if (response.status != successStatus) return AuthResult.Failure(response.toError())

        return try {
            AuthResult.Success(decode(response.body))
        } catch (_: IllegalArgumentException) {
            AuthResult.Failure(AuthError(AuthErrorCode.UnexpectedResponse))
        }
    }

    private fun isPolicyDocumentUrl(url: String): Boolean {
        if (!url.endsWith(PolicyDocumentPath)) return false
        val documentOrigin = url.removeSuffix(PolicyDocumentPath)
        return documentOrigin == origin || documentOrigin.isOrigin(SecureOrigin)
    }

    private fun String.isOrigin(pattern: Regex): Boolean {
        val match = pattern.matchEntire(this) ?: return false
        val port = match.groupValues[1]
        return port.isEmpty() || port.toInt() in 1..65535
    }

    private fun AuthHttpResponse.toError(): AuthError {
        val error = try {
            AuthJson.decodeFromString<ErrorEnvelopeDto>(body).error
        } catch (_: IllegalArgumentException) {
            return AuthError(if (status == 503) AuthErrorCode.ServiceUnavailable else AuthErrorCode.UnexpectedResponse)
        }
        val code = when {
            status == 401 && error.code == "INVALID_CREDENTIALS" -> AuthErrorCode.InvalidCredentials
            status == 401 && error.code == "SESSION_EXPIRED" -> AuthErrorCode.SessionExpired
            status == 401 && error.code == "AUTH_REQUIRED" -> AuthErrorCode.AuthRequired
            status == 409 && error.code == "DUPLICATE_PHONE" -> AuthErrorCode.DuplicatePhone
            status == 422 && error.code == "PRIVACY_POLICY_ACCEPTANCE_REQUIRED" -> AuthErrorCode.PolicyAcceptanceRequired
            status in setOf(400, 413, 422) && error.code in setOf("INVALID_FIELD", "INVALID_JSON") -> AuthErrorCode.InvalidField
            status == 429 -> AuthErrorCode.RateLimited
            status == 503 -> AuthErrorCode.ServiceUnavailable
            else -> AuthErrorCode.UnexpectedResponse
        }
        val field = when (code) {
            AuthErrorCode.DuplicatePhone -> AuthField.Phone
            AuthErrorCode.PolicyAcceptanceRequired -> AuthField.Consent
            else -> null
        }
        val currentVersion = (error.details?.get("currentVersion") as? JsonPrimitive)
            ?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        return AuthError(code, field, currentVersion, error.retryAfterSeconds?.takeIf { it > 0 })
    }

    private companion object {
        val SecureOrigin = Regex("""https://[A-Za-z0-9.-]+(?::([0-9]{1,5}))?""")
        val LocalOrigin = Regex("""http://127\.0\.0\.1(?::([0-9]{1,5}))?""")
        const val PolicyDocumentPath = "/api/v1/legal/privacy/document"
    }
}
