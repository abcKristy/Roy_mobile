package com.example.roy.domain.auth

import kotlin.time.Instant

internal interface AuthRepository {
    suspend fun login(credentials: AuthCredentials.Login): AuthResult<AuthSession>
    suspend fun register(credentials: AuthCredentials.Register): AuthResult<AuthSession>
    suspend fun refresh(refreshToken: String): AuthResult<AuthSession>
    suspend fun currentUser(accessToken: String): AuthResult<AuthUser>
    suspend fun logout(accessToken: String): AuthResult<Unit>
    suspend fun privacyPolicy(): AuthResult<PrivacyPolicy>
}

internal class AuthSession(
    val accessToken: String,
    val expiresAt: Instant,
    val refreshToken: String,
    val refreshExpiresAt: Instant,
    val user: AuthUser,
    val isOfflineRestoreAllowed: Boolean = true,
)

internal class AuthUser(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val phone: String,
    val ageYears: Int,
    val messageMutedUntil: Instant?,
    val createdAt: Instant,
)

internal data class PrivacyPolicy(
    val version: String,
    val publishedAt: Instant,
    val url: String,
)

internal sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

internal data class AuthError(
    val code: AuthErrorCode,
    val field: AuthField? = null,
    val currentVersion: String? = null,
    val retryAfterSeconds: Int? = null,
)

internal enum class AuthErrorCode {
    InvalidCredentials,
    SessionExpired,
    AuthRequired,
    DuplicatePhone,
    InvalidField,
    PolicyAcceptanceRequired,
    RateLimited,
    ServiceUnavailable,
    Network,
    UnexpectedResponse,
    Storage,
}
