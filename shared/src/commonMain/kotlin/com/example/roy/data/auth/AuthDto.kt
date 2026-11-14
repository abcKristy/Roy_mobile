package com.example.roy.data.auth

import com.example.roy.domain.auth.AuthSession
import com.example.roy.domain.auth.AuthUser
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
internal class LoginDto(val phone: String, val password: String)

@Serializable
internal class RegisterDto(
    val phone: String,
    val password: String,
    val displayName: String,
    val ageYears: Int,
    val acceptedPrivacyPolicyVersion: String,
)

@Serializable
internal class RefreshDto(val refreshToken: String)

@Serializable
internal class UserDto(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val phone: String,
    val ageYears: Int,
    val messageMutedUntil: String? = null,
    val createdAt: String,
) {
    fun toDomain(): AuthUser = AuthUser(
        id, displayName, avatarUrl, phone, ageYears, messageMutedUntil?.let(Instant::parse), Instant.parse(createdAt)
    )
}

@Serializable
internal class SessionDto(
    val accessToken: String,
    val expiresAt: String,
    val refreshToken: String,
    val refreshExpiresAt: String,
    val user: UserDto,
) {
    fun toDomain(): AuthSession {
        require(accessToken.isNotBlank() && refreshToken.isNotBlank())
        return AuthSession(accessToken, Instant.parse(expiresAt), refreshToken, Instant.parse(refreshExpiresAt), user.toDomain())
    }
}

@Serializable
internal class PrivacyPolicyDto(val version: String, val publishedAt: String, val url: String)

@Serializable
internal class ErrorEnvelopeDto(val error: ErrorDto)

@Serializable
internal class ErrorDto(
    val code: String,
    val details: JsonObject? = null,
    val retryAfterSeconds: Int? = null,
)

internal val AuthJson = Json { ignoreUnknownKeys = true }

internal object AuthSessionCodec {
    fun encode(session: AuthSession): String = AuthJson.encodeToString(
        SessionDto(
            accessToken = session.accessToken,
            expiresAt = session.expiresAt.toString(),
            refreshToken = session.refreshToken,
            refreshExpiresAt = session.refreshExpiresAt.toString(),
            user = with(session.user) {
                UserDto(id, displayName, avatarUrl, phone, ageYears, messageMutedUntil?.toString(), createdAt.toString())
            },
        ),
    )

    fun decode(encoded: String): AuthSession = AuthJson.decodeFromString<SessionDto>(encoded).toDomain()
}
