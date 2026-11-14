package com.example.roy.domain.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val restoreGate = Mutex()
    private val logoutGate = Mutex()
    private val status = MutableStateFlow<SessionStatus>(SessionStatus.Restoring)
    private var scopeId = 0L
    private var closing = false
    private var pendingClear = false
    private var session: AuthSession? = null
    private var readWave = ReadWave()
    private var inFlight: CompletableDeferred<AuthResult<AuthSession>>? = null
    private var uncommitted: AuthSession? = null

    val state = status.asStateFlow()

    suspend fun restore(): Unit = restoreGate.withLock {
        val saved = gate.withLock {
            if (pendingClear) {
                endSession()
                return
            }
            if (status.value is SessionStatus.Active || status.value == SessionStatus.Guest) return
            try {
                store.read().also { session = it }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                status.value = SessionStatus.StorageUnavailable
                return
            }
        }
        if (saved == null) {
            gate.withLock { status.value = SessionStatus.Guest }
            return
        }
        when (val result = currentUser()) {
            is AuthResult.Success -> gate.withLock {
                val current = session ?: return@withLock
                if (current.user.id != result.value.id) {
                    status.value = SessionStatus.VerificationUnavailable
                    return@withLock
                }
                // The rotation path already committed the rotated snapshot.
                val savedProfile = if (current !== saved) AuthResult.Success(current) else persist(current.withUser(result.value))
                if (savedProfile is AuthResult.Success) {
                    status.value = SessionStatus.Active(scopeId)
                } else {
                    status.value = SessionStatus.StorageUnavailable
                }
            }
            is AuthResult.Failure -> gate.withLock {
                if (session != null) {
                    status.value = if (result.error.code == AuthErrorCode.Storage) {
                        SessionStatus.StorageUnavailable
                    } else {
                        SessionStatus.VerificationUnavailable
                    }
                }
            }
        }
    }

    suspend fun authenticate(credentials: AuthCredentials): AuthResult<Unit> = gate.withLock {
        if (closing || status.value != SessionStatus.Guest) return AuthResult.Failure(AuthError(AuthErrorCode.UnexpectedResponse))
        val result = when (credentials) {
            is AuthCredentials.Login -> repository.login(credentials)
            is AuthCredentials.Register -> repository.register(credentials)
        }
        when (result) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> when (val write = persist(result.value)) {
                is AuthResult.Failure -> write
                is AuthResult.Success -> {
                    status.value = SessionStatus.Active(++scopeId)
                    AuthResult.Success(Unit)
                }
            }
        }
    }

    suspend fun policy(): AuthResult<PrivacyPolicy> = repository.privacyPolicy()

    suspend fun currentUser(): AuthResult<AuthUser> {
        val (observed, observedScope, wave) = gate.withLock {
            val current = session?.takeUnless { closing } ?: return expired()
            if (readWave.result?.isCompleted == true) readWave = ReadWave()
            Triple(current, scopeId, readWave)
        }
        val first = repository.currentUser(observed.accessToken)
        if (gate.withLock { scopeId != observedScope }) return expired()
        if (!first.isTerminalAccess()) return first
        return when (val refreshed = refresh(observed, observedScope, wave)) {
            is AuthResult.Failure -> refreshed
            is AuthResult.Success -> {
                val retry = repository.currentUser(refreshed.value.accessToken)
                if (gate.withLock { scopeId != observedScope }) expired() else retry
            }
        }
    }

    suspend fun logout(): AuthResult<Unit> = logoutGate.withLock {
        val closingScope = gate.withLock {
            if (session == null) return AuthResult.Success(Unit)
            closing = true
            scopeId
        }
        try {
            gate.withLock { inFlight }?.await()
            val latest = gate.withLock { session } ?: return AuthResult.Success(Unit)
            // Repeat only after an explicit rejection of the original access credentials.
            val initial = repository.logout(latest.accessToken)
            val result = if (initial.isTerminalAccess()) {
                when (val refreshed = refresh(latest, closingScope, ReadWave(), forLogout = true)) {
                    is AuthResult.Success -> repository.logout(refreshed.value.accessToken)
                    is AuthResult.Failure -> refreshed
                }
            } else initial
            when {
                result is AuthResult.Success || result.isTerminalAccess() -> withContext(NonCancellable) {
                    gate.withLock {
                        if (session != null && scopeId == closingScope) endSession()
                        else AuthResult.Success(Unit)
                    }
                }
                else -> result
            }
        } finally {
            withContext(NonCancellable) { gate.withLock { closing = false } }
        }
    }

    private suspend fun refresh(observed: AuthSession, observedScope: Long, wave: ReadWave, forLogout: Boolean = false): AuthResult<AuthSession> {
        val (flight, owner) = gate.withLock {
            val current = session?.takeUnless { closing && !forLogout } ?: return expired()
            if (scopeId != observedScope || current.user.id != observed.user.id) return expired()
            if (current.refreshToken != observed.refreshToken) return AuthResult.Success(current)
            val running = wave.result ?: inFlight
            if (running != null) {
                wave.result = running
                running to false
            } else {
                CompletableDeferred<AuthResult<AuthSession>>().also {
                    wave.result = it
                    inFlight = it
                } to true
            }
        }
        if (!owner) return flight.await()
        // The owner finishes committing a rotation even if its requesting screen disappears.
        return withContext(NonCancellable) {
            try {
                val result = rotate(observed)
                val applied = gate.withLock {
                    val current = session
                    if (scopeId != observedScope || current !== observed) return@withLock expired()
                    when (result) {
                        is AuthResult.Success -> {
                            uncommitted = result.value
                            persist(result.value).also { if (it is AuthResult.Success) uncommitted = null }
                        }
                        is AuthResult.Failure -> {
                            if (result.isTerminalAccess()) {
                                when (val cleared = endSession()) {
                                    is AuthResult.Failure -> cleared
                                    is AuthResult.Success -> result
                                }
                            } else result
                        }
                    }
                }
                flight.complete(applied)
                applied
            } catch (failure: Exception) {
                flight.completeExceptionally(failure)
                throw failure
            } finally {
                gate.withLock { if (inFlight === flight) inFlight = null }
            }
        }
    }

    private suspend fun rotate(observed: AuthSession): AuthResult<AuthSession> {
        val pending = gate.withLock { uncommitted }
        if (pending != null) return AuthResult.Success(pending)
        val result = repository.refresh(observed.refreshToken)
        if (result is AuthResult.Success && result.value.user.id != observed.user.id) {
            return AuthResult.Failure(AuthError(AuthErrorCode.UnexpectedResponse))
        }
        return result
    }

    private class ReadWave {
        var result: CompletableDeferred<AuthResult<AuthSession>>? = null
    }

    private suspend fun persist(saved: AuthSession): AuthResult<AuthSession> = withContext(NonCancellable) {
        try {
            store.replace(saved)
            session = saved
            AuthResult.Success(saved)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            storageFailure()
        }
    }

    private suspend fun endSession(): AuthResult<Unit> = withContext(NonCancellable) {
        pendingClear = true
        session = null
        readWave = ReadWave()
        uncommitted = null
        scopeId++
        try {
            store.clear()
            pendingClear = false
            status.value = SessionStatus.Guest
            AuthResult.Success(Unit)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            status.value = SessionStatus.StorageUnavailable
            storageFailure()
        }
    }

    private fun expired() = AuthResult.Failure(AuthError(AuthErrorCode.SessionExpired))
    private fun storageFailure() = AuthResult.Failure(AuthError(AuthErrorCode.Storage))
}

private fun AuthResult<*>.isTerminalAccess() = this is AuthResult.Failure &&
    (error.code == AuthErrorCode.SessionExpired || error.code == AuthErrorCode.AuthRequired)

private fun AuthSession.withUser(user: AuthUser) =
    AuthSession(accessToken, expiresAt, refreshToken, refreshExpiresAt, user)
