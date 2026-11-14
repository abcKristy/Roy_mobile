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
    private val status = MutableStateFlow<SessionStatus>(SessionStatus.Restoring)
    private var scopeId = 0L
    private var session: AuthSession? = null
    private var readWave = ReadWave()
    private var inFlight: CompletableDeferred<AuthResult<AuthSession>>? = null
    private var uncommitted: AuthSession? = null
    private var unconfirmed: AuthSession? = null

    val state = status.asStateFlow()

    suspend fun restore(): Unit = restoreGate.withLock {
        val saved = gate.withLock {
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
                // The rotation path already committed the verified snapshot.
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
        if (status.value != SessionStatus.Guest) return AuthResult.Failure(AuthError(AuthErrorCode.UnexpectedResponse))
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
            val current = session ?: return expired()
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

    private suspend fun refresh(observed: AuthSession, observedScope: Long, wave: ReadWave): AuthResult<AuthSession> {
        val (flight, owner) = gate.withLock {
            val current = session ?: return expired()
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
                            unconfirmed = null
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
        val unverified = gate.withLock { unconfirmed }
        if (unverified != null) {
            val previousCheck = verifyIdentity(unverified, observed)
            if (!previousCheck.isTerminalAccess()) return previousCheck
        }
        return when (val result = repository.refresh(unverified?.refreshToken ?: observed.refreshToken)) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> {
                val rotated = result.value
                if (rotated.user.id == observed.user.id) return result
                val verified = verifyIdentity(rotated, observed)
                if (verified is AuthResult.Failure) {
                    // Preserve the rotated pair for the next explicit verification or refresh.
                    gate.withLock { if (session === observed) unconfirmed = rotated }
                    if (verified.isTerminalAccess()) {
                        return AuthResult.Failure(AuthError(AuthErrorCode.UnexpectedResponse))
                    }
                }
                verified
            }
        }
    }

    private suspend fun verifyIdentity(rotated: AuthSession, observed: AuthSession): AuthResult<AuthSession> =
        when (val profile = repository.currentUser(rotated.accessToken)) {
            is AuthResult.Success -> if (profile.value.id == observed.user.id) {
                AuthResult.Success(rotated.withUser(profile.value))
            } else {
                AuthResult.Failure(AuthError(AuthErrorCode.UnexpectedResponse))
            }
            is AuthResult.Failure -> profile
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
        session = null
        readWave = ReadWave()
        uncommitted = null
        unconfirmed = null
        scopeId++
        try {
            store.clear()
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
