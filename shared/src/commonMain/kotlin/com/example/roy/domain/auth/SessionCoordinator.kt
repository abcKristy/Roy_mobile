package com.example.roy.domain.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal sealed interface SessionStatus {
    data object Restoring : SessionStatus
    data object Guest : SessionStatus
    data class Active(val scopeId: Long) : SessionStatus
    data object StorageUnavailable : SessionStatus
    data object VerificationUnavailable : SessionStatus
}

internal class SessionCoordinator(
    private val repository: AuthRepository,
    private val store: SessionStore,
) {
    private val gate = Mutex()
    private val status = MutableStateFlow<SessionStatus>(SessionStatus.Restoring)
    private var scopeId = 0L
    private var session: AuthSession? = null

    val state = status.asStateFlow()

    suspend fun restore() = serialized {
        if (status.value is SessionStatus.Active || status.value == SessionStatus.Guest) return@serialized
        try {
            val saved = store.read()
            if (saved == null) {
                status.value = SessionStatus.Guest
            } else {
                restoreSaved(saved)
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            status.value = SessionStatus.StorageUnavailable
        }
    }

    suspend fun authenticate(credentials: AuthCredentials): AuthResult<Unit> = serialized {
        if (status.value != SessionStatus.Guest) return@serialized AuthResult.Failure(
            AuthError(AuthErrorCode.UnexpectedResponse),
        )
        val result = when (credentials) {
            is AuthCredentials.Login -> repository.login(credentials)
            is AuthCredentials.Register -> repository.register(credentials)
        }
        when (result) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> persistAndActivate(result.value)
        }
    }

    suspend fun policy(): AuthResult<PrivacyPolicy> = repository.privacyPolicy()

    private suspend fun restoreSaved(saved: AuthSession) {
        // The protected read revalidates the snapshot. Refresh is coordinated here once available.
        when (val result = repository.currentUser(saved.accessToken)) {
            is AuthResult.Success -> {
                check(result.value.id == saved.user.id)
                persistAndActivate(saved.withUser(result.value), restoring = true)
            }
            is AuthResult.Failure -> status.value = SessionStatus.VerificationUnavailable
        }
    }

    private suspend fun persistAndActivate(
        saved: AuthSession,
        restoring: Boolean = false,
    ): AuthResult<Unit> = withContext(NonCancellable) {
        try {
            store.replace(saved)
            activate(saved)
            AuthResult.Success(Unit)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (restoring) status.value = SessionStatus.StorageUnavailable
            AuthResult.Failure(AuthError(AuthErrorCode.Storage))
        }
    }

    private fun activate(saved: AuthSession) {
        session = saved
        status.value = SessionStatus.Active(++scopeId)
    }

    private suspend fun <T> serialized(operation: suspend () -> T): T {
        gate.lock()
        return try {
            operation()
        } finally {
            gate.unlock()
        }
    }
}

internal fun AuthSession.withUser(user: AuthUser) =
    AuthSession(accessToken, expiresAt, refreshToken, refreshExpiresAt, user)
