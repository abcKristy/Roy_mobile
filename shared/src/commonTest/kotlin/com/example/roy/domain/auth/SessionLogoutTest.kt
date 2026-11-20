package com.example.roy.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLogoutTest {
    @Test
    fun failedClearPurgesPrivateCacheAndCannotRestoreRevokedSessionOffline() = runTest {
        val repository = SessionTestRepository()
        val store = object : SessionStore {
            var snapshot: AuthSession? = repository.first
            override suspend fun read() = snapshot
            override suspend fun replace(session: AuthSession) { snapshot = session }
            override suspend fun clear() { error("fixture clear failure") }
        }
        var purges = 0
        val coordinator = SessionCoordinator(repository, store, { purges++ }, allowOfflineRestore = true)
        coordinator.restore()
        repository.revoke = { AuthResult.Success(Unit) }

        assertEquals(AuthErrorCode.Storage, assertIs<AuthResult.Failure>(coordinator.logout()).error.code)
        assertEquals(1, purges)
        val restarted = SessionCoordinator(repository, store, allowOfflineRestore = true)
        restarted.setOnline(false)
        restarted.restore()
        assertEquals(SessionStatus.VerificationUnavailable, restarted.state.value)
    }

    @Test
    fun failedLogoutIntentWriteDoesNotRevokeServerSession() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore().apply { saved = repository.first }
        val coordinator = SessionCoordinator(repository, store, allowOfflineRestore = true)
        coordinator.restore()
        val active = coordinator.state.value
        var revocations = 0
        repository.revoke = { revocations++; AuthResult.Success(Unit) }
        store.beforeWrite = { error("fixture intent write failure") }

        assertEquals(AuthErrorCode.Storage, assertIs<AuthResult.Failure>(coordinator.logout()).error.code)
        assertEquals(0, revocations)
        assertEquals(active, coordinator.state.value)
    }

    @Test
    fun onlySuccessfulOnlineVerificationReenablesOfflineRestoreAfterUncertainLogout() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore().apply { saved = repository.first }
        val coordinator = SessionCoordinator(repository, store, allowOfflineRestore = true)
        coordinator.restore()
        repository.revoke = { AuthResult.Failure(AuthError(AuthErrorCode.Network)) }
        assertIs<AuthResult.Failure>(coordinator.logout())

        val offlineRestart = SessionCoordinator(repository, store, allowOfflineRestore = true)
        offlineRestart.setOnline(false)
        offlineRestart.restore()
        assertEquals(SessionStatus.VerificationUnavailable, offlineRestart.state.value)
        offlineRestart.setOnline(true)
        offlineRestart.restore()
        assertIs<SessionStatus.Active>(offlineRestart.state.value)
        val verifiedOfflineRestart = SessionCoordinator(repository, store, allowOfflineRestore = true)
        verifiedOfflineRestart.setOnline(false)
        verifiedOfflineRestart.restore()
        assertIs<SessionStatus.Active>(verifiedOfflineRestart.state.value)
    }

    @Test
    fun terminalRevocationMarksSnapshotAndAttemptsEveryPrivateCleanup() = runTest {
        val repository = SessionTestRepository()
        val store = object : SessionStore {
            var snapshot: AuthSession? = repository.first
            override suspend fun read() = snapshot
            override suspend fun replace(session: AuthSession) { snapshot = session }
            override suspend fun clear() { error("fixture clear failure") }
        }
        var purges = 0
        var bindingFailure = false
        val coordinator = SessionCoordinator(repository, store, { purges++ }, allowOfflineRestore = true,
            onAccountChanged = { if (bindingFailure) error("fixture binding failure") })
        coordinator.restore()
        val scope = assertIs<SessionStatus.Active>(coordinator.state.value).scopeId
        bindingFailure = true
        coordinator.invalidateRevokedScope(scope, repository.user.id)

        assertEquals(1, purges)
        assertEquals(SessionStatus.StorageUnavailable, coordinator.state.value)
        assertEquals(false, store.snapshot?.isOfflineRestoreAllowed)
        val restarted = SessionCoordinator(repository, store, allowOfflineRestore = true)
        restarted.setOnline(false)
        restarted.restore()
        assertEquals(SessionStatus.VerificationUnavailable, restarted.state.value)
    }

    @Test
    fun logoutWaitsForRotationThenRevokesAndClearsTheNewPair() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        coordinator.authenticate(AuthCredentials.Login("+123", "fixture"))
        val rotateNow = CompletableDeferred<Unit>()
        val next = repository.rotated()
        var revokedToken: String? = null
        repository.profile = { token ->
            if (token == next.accessToken) AuthResult.Success(repository.user) else denied()
        }
        repository.rotation = { rotateNow.await(); AuthResult.Success(next) }
        repository.revoke = { revokedToken = it; AuthResult.Success(Unit) }

        val request = async { coordinator.currentUser() }
        runCurrent()
        val logout = async { coordinator.logout() }
        runCurrent()
        assertEquals(null, revokedToken)
        rotateNow.complete(Unit)
        logout.await()
        request.await()

        assertEquals(next.accessToken, revokedToken)
        assertEquals(null, store.saved)
        assertEquals(SessionStatus.Guest, coordinator.state.value)
    }

    @Test
    fun repeatedExplicitUnauthorizedEndsLocalSession() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        coordinator.authenticate(AuthCredentials.Login("+123", "fixture"))
        repository.rotation = { AuthResult.Success(repository.rotated()) }
        var revokes = 0
        repository.revoke = { revokes++; denied() }

        assertIs<AuthResult.Success<Unit>>(coordinator.logout())

        assertEquals(2, revokes)
        assertEquals(null, store.saved)
        assertEquals(SessionStatus.Guest, coordinator.state.value)
    }

    @Test
    fun failedLocalClearKeepsRevokedPairInactiveOnANewOfflineCoordinator() = runTest {
        val repository = SessionTestRepository()
        val store = object : SessionStore {
            var snapshot: AuthSession? = null
            override suspend fun read() = snapshot
            override suspend fun replace(session: AuthSession) { snapshot = session }
            override suspend fun clear() { error("fixture failure") }
        }
        val first = SessionCoordinator(repository, store)
        first.restore()
        first.authenticate(AuthCredentials.Login("+123", "fixture"))
        repository.revoke = { AuthResult.Success(Unit) }

        assertEquals(AuthErrorCode.Storage, assertIs<AuthResult.Failure>(first.logout()).error.code)
        assertEquals(SessionStatus.StorageUnavailable, first.state.value)

        repository.profile = { AuthResult.Failure(AuthError(AuthErrorCode.Network)) }
        val restarted = SessionCoordinator(repository, store)
        restarted.restore()
        assertEquals(SessionStatus.VerificationUnavailable, restarted.state.value)
        assertEquals(repository.first.accessToken, store.read()?.accessToken)
        assertEquals(false, store.read()?.isOfflineRestoreAllowed)
    }

    @Test
    fun uncertainNetworkFailureKeepsTheSessionForExplicitRetry() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        coordinator.authenticate(AuthCredentials.Login("+123", "fixture"))
        var revokes = 0
        repository.revoke = { revokes++; AuthResult.Failure(AuthError(AuthErrorCode.Network)) }

        assertIs<AuthResult.Failure>(coordinator.logout())

        assertEquals(1, revokes)
        assertEquals(repository.first.accessToken, store.saved?.accessToken)
        assertEquals(false, store.saved?.isOfflineRestoreAllowed)
        assertIs<SessionStatus.Active>(coordinator.state.value)
    }
}
