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
    private val onPrivateSessionEnded: suspend () -> Unit = {},
    private val allowOfflineRestore: Boolean = false,
    private val onAccountChanged: (String?) -> Unit = {},
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
    private var online = true
    private var requiresReconnectVerification = false

    val state = status.asStateFlow()
    private val expirationId = MutableStateFlow(0L)
    val expiration = expirationId.asStateFlow()

    suspend fun setOnline(value: Boolean) {
        gate.withLock { online = value }
        if (value) verifyReconnectedSession()
    }

    private suspend fun verifyReconnectedSession() {
        restoreGate.withLock {
            val expected = gate.withLock {
                (status.value as? SessionStatus.Active)?.takeIf { requiresReconnectVerification && online }
            } ?: return@withLock
            currentUser(expected.scopeId)
        }
    }

    suspend fun <T> withOfflineAccount(expectedScopeId: Long, use: suspend (String) -> T): T? = gate.withLock {
        if (closing || status.value != SessionStatus.Active(expectedScopeId)) return@withLock null
        session?.user?.id?.let { use(it) }
    }

    suspend fun cachedUser(expectedScopeId: Long): AuthUser? = gate.withLock {
        session?.user?.takeIf { !closing && status.value == SessionStatus.Active(expectedScopeId) }
    }

    suspend fun updateCachedUser(expectedScopeId: Long, user: AuthUser): Boolean = gate.withLock {
        val current = session ?: return@withLock false
        if (closing || status.value != SessionStatus.Active(expectedScopeId) || current.user.id != user.id) {
            return@withLock false
        }
        if (current.user != user) {
            val updated = current.withUser(user)
            if (persist(updated) is AuthResult.Failure) session = updated
        }
        true
    }

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
                onAccountChanged(null)
                status.value = SessionStatus.StorageUnavailable
                return
            }
        }
        if (saved == null) {
            gate.withLock {
                onAccountChanged(null)
                status.value = SessionStatus.Guest
            }
            return
        }
        when (val result = currentUser()) {
            is AuthResult.Success -> gate.withLock {
                val current = session ?: return@withLock
                if (current.user.id != result.value.id) {
                    onAccountChanged(null)
                    status.value = SessionStatus.VerificationUnavailable
                    return@withLock
                }
                // Only successful server verification permits offline reads after an interrupted logout.
                val verified = if (current !== saved) current else current.withUser(result.value)
                val savedProfile = if (current !== saved && verified.isOfflineRestoreAllowed) AuthResult.Success(verified)
                    else persist(verified.withOfflineRestoreAllowed(true))
                if (savedProfile is AuthResult.Success) {
                    onAccountChanged(current.user.id)
                    status.value = SessionStatus.Active(scopeId)
                } else {
                    onAccountChanged(null)
                    status.value = SessionStatus.StorageUnavailable
                }
            }
            is AuthResult.Failure -> gate.withLock {
                if (session != null) {
                    if (allowOfflineRestore && session?.isOfflineRestoreAllowed == true && result.error.code == AuthErrorCode.Network) {
                        requiresReconnectVerification = true
                        onAccountChanged(session?.user?.id)
                        status.value = SessionStatus.Active(scopeId)
                        return@withLock
                    }
                    onAccountChanged(null)
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
        if (!online) return networkUnavailable()
        val result = when (credentials) {
            is AuthCredentials.Login -> repository.login(credentials)
            is AuthCredentials.Register -> repository.register(credentials)
        }
        when (result) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> when (val write = persist(result.value)) {
                is AuthResult.Failure -> write
                is AuthResult.Success -> {
                    onAccountChanged(write.value.user.id)
                    status.value = SessionStatus.Active(++scopeId)
                    AuthResult.Success(Unit)
                }
            }
        }
    }

    suspend fun policy(): AuthResult<PrivacyPolicy> = repository.privacyPolicy()

    suspend fun currentUser(): AuthResult<AuthUser> = readProtected(read = repository::currentUser)

    suspend fun currentUser(expectedScopeId: Long): AuthResult<AuthUser> {
        val result = readProtected(expectedScopeId, repository::currentUser)
        if (result !is AuthResult.Success) return result
        return gate.withLock {
            if (closing || status.value != SessionStatus.Active(expectedScopeId)) return@withLock expired()
            if (session?.user?.id != result.value.id) {
                val cleared = endSession(expired = true)
                return@withLock if (cleared is AuthResult.Failure) cleared else expired()
            }
            if (online) requiresReconnectVerification = false
            result
        }
    }

    /** The callback must be a read. A credential rejection can cause one retry. */
    suspend fun <T> readProtected(
        expectedScopeId: Long? = null,
        read: suspend (String) -> AuthResult<T>,
    ): AuthResult<T> {
        val (observed, observedScope, wave) = gate.withLock {
            if (expectedScopeId != null && status.value != SessionStatus.Active(expectedScopeId)) return expired()
            val current = session?.takeUnless { closing } ?: return expired()
            if (!online) return networkUnavailable()
            if (readWave.result?.isCompleted == true) readWave = ReadWave()
            Triple(current, scopeId, readWave)
        }
        val first = read(observed.accessToken)
        if (gate.withLock { scopeId != observedScope }) return expired()
        if (!first.isTerminalAccess()) return first
        return when (val refreshed = refresh(observed, observedScope, wave)) {
            is AuthResult.Failure -> refreshed
            is AuthResult.Success -> {
                val retry = read(refreshed.value.accessToken)
                gate.withLock {
                    if (scopeId != observedScope) return@withLock expired()
                    if (retry.isTerminalAccess() && session?.refreshToken == refreshed.value.refreshToken && !closing) {
                        val cleared = endSession(expired = true)
                        if (cleared is AuthResult.Failure) return@withLock cleared
                    }
                    retry
                }
            }
        }
    }

    /** Dispatches a user-initiated write once. Refresh may prepare the next explicit attempt. */
    suspend fun <T> mutateProtected(
        expectedScopeId: Long,
        mutate: suspend (String) -> AuthResult<T>,
    ): AuthResult<T> {
        if (gate.withLock { online && requiresReconnectVerification && status.value == SessionStatus.Active(expectedScopeId) }) {
            verifyReconnectedSession()
        }
        val (observed, wave) = gate.withLock {
            if (status.value != SessionStatus.Active(expectedScopeId)) return expired()
            val current = session?.takeUnless { closing } ?: return expired()
            if (!online || requiresReconnectVerification) return networkUnavailable()
            if (readWave.result?.isCompleted == true) readWave = ReadWave()
            current to readWave
        }
        val result = mutate(observed.accessToken)
        if (gate.withLock { scopeId != expectedScopeId || closing }) return expired()
        if (!result.isTerminalAccess()) return result
        return when (val refreshed = refresh(observed, expectedScopeId, wave)) {
            is AuthResult.Success -> result
            is AuthResult.Failure -> refreshed
        }
    }

    suspend fun invalidateRevokedScope(expectedScopeId: Long, userId: String): Unit = gate.withLock {
        if (!closing && status.value == SessionStatus.Active(expectedScopeId) && session?.user?.id == userId) {
            endSession(expired = true)
        }
    }

    suspend fun completeRevokedProfileSession(expectedScopeId: Long, userId: String, expired: Boolean): AuthResult<Unit> =
        gate.withLock {
            if (closing || status.value != SessionStatus.Active(expectedScopeId) || session?.user?.id != userId) {
                return@withLock this.expired()
            }
            endSession(expired)
        }

    suspend fun logout(expectedScopeId: Long? = null): AuthResult<Unit> = logoutGate.withLock {
        verifyReconnectedSession()
        val closingScope = gate.withLock {
            if (expectedScopeId != null && status.value != SessionStatus.Active(expectedScopeId)) return expired()
            val current = session ?: return AuthResult.Success(Unit)
            if (online && requiresReconnectVerification) return networkUnavailable()
            // Persist intent before remote revocation; failed deletion must not resurrect an offline session.
            if (current.isOfflineRestoreAllowed) {
                val marked = persist(current.withOfflineRestoreAllowed(false))
                if (marked is AuthResult.Failure) return marked
            }
            if (!online) return endSession()
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
                    if (scopeId != observedScope || current?.refreshToken != observed.refreshToken) return@withLock expired()
                    when (result) {
                        is AuthResult.Success -> {
                            val refreshed = if (current === observed) result.value else result.value.withUser(current.user)
                            val rotated = refreshed.withOfflineRestoreAllowed(current.isOfflineRestoreAllowed)
                            uncommitted = rotated
                            persist(rotated).also { if (it is AuthResult.Success) uncommitted = null }
                        }
                        is AuthResult.Failure -> {
                            if (result.isTerminalAccess()) {
                                when (val cleared = endSession(expired = !forLogout && !closing)) {
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

    private suspend fun endSession(expired: Boolean = false): AuthResult<Unit> = withContext(NonCancellable) {
        pendingClear = true
        // Terminal server revocation also needs a durable guard if record deletion fails.
        session?.takeIf { it.isOfflineRestoreAllowed }?.let { persist(it.withOfflineRestoreAllowed(false)) }
        session = null
        readWave = ReadWave()
        uncommitted = null
        requiresReconnectVerification = false
        scopeId++
        var cleanupFailed = false
        suspend fun attempt(cleanup: suspend () -> Unit) {
            try {
                cleanup()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                cleanupFailed = true
            }
        }
        // Each private surface must be cleared even when another storage backend fails.
        attempt { onAccountChanged(null) }
        attempt { store.clear() }
        attempt { onPrivateSessionEnded() }
        if (cleanupFailed) {
            status.value = SessionStatus.StorageUnavailable
            storageFailure()
        } else {
            pendingClear = false
            status.value = SessionStatus.Guest
            if (expired) expirationId.value++
            AuthResult.Success(Unit)
        }
    }

    private fun expired() = AuthResult.Failure(AuthError(AuthErrorCode.SessionExpired))
    private fun storageFailure() = AuthResult.Failure(AuthError(AuthErrorCode.Storage))
    private fun networkUnavailable() = AuthResult.Failure(AuthError(AuthErrorCode.Network))
}

private fun AuthResult<*>.isTerminalAccess() = this is AuthResult.Failure &&
    (error.code == AuthErrorCode.SessionExpired || error.code == AuthErrorCode.AuthRequired)

private fun AuthSession.withUser(user: AuthUser) =
    AuthSession(accessToken, expiresAt, refreshToken, refreshExpiresAt, user, isOfflineRestoreAllowed)

private fun AuthSession.withOfflineRestoreAllowed(allowed: Boolean) =
    if (isOfflineRestoreAllowed == allowed) this
    else AuthSession(accessToken, expiresAt, refreshToken, refreshExpiresAt, user, allowed)
