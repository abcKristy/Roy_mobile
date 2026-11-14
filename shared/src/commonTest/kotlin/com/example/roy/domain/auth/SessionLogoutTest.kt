package com.example.roy.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLogoutTest {
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
        assertSame(repository.first, store.read())
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
        assertSame(repository.first, store.saved)
        assertIs<SessionStatus.Active>(coordinator.state.value)
    }
}
