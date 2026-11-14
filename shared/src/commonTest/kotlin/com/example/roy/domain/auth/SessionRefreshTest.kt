package com.example.roy.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRefreshTest {
    @Test
    fun concurrentExpiredReadsShareOneRotationAndRetryRead() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        coordinator.authenticate(AuthCredentials.Login("+123", "fixture"))
        val rotated = repository.rotated()
        val allowed = CompletableDeferred<Unit>()
        var refreshes = 0
        repository.profile = { token ->
            if (token == rotated.accessToken) AuthResult.Success(repository.user) else denied()
        }
        repository.rotation = {
            refreshes++
            allowed.await()
            AuthResult.Success(rotated)
        }

        val reads = List(12) { async { coordinator.currentUser() } }
        runCurrent()
        assertEquals(1, refreshes)
        allowed.complete(Unit)

        reads.awaitAll().forEach { assertIs<AuthResult.Success<AuthUser>>(it) }
        assertSame(rotated, store.saved)
        assertEquals(1, refreshes)
    }

    @Test
    fun temporaryRefreshFailureKeepsTheSnapshotAndSharesFailure() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        coordinator.authenticate(AuthCredentials.Login("+123", "fixture"))
        val allowed = CompletableDeferred<Unit>()
        repository.profile = { denied() }
        var refreshes = 0
        repository.rotation = {
            refreshes++
            allowed.await()
            AuthResult.Failure(AuthError(AuthErrorCode.Network))
        }

        val reads = List(8) { async { coordinator.currentUser() } }
        runCurrent()
        allowed.complete(Unit)

        reads.awaitAll().forEach { assertEquals(AuthErrorCode.Network, assertIs<AuthResult.Failure>(it).error.code) }
        assertEquals(1, refreshes)
        assertSame(repository.first, store.saved)
        assertIs<SessionStatus.Active>(coordinator.state.value)
    }

    @Test
    fun lateUnauthorizedFromTheSameReadWaveDoesNotResendAConsumedToken() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        coordinator.authenticate(AuthCredentials.Login("+123", "fixture"))
        val releaseLate401 = CompletableDeferred<Unit>()
        var reads = 0
        var rotations = 0
        repository.profile = {
            if (++reads == 2) releaseLate401.await()
            denied()
        }
        repository.rotation = {
            rotations++
            kotlinx.coroutines.yield()
            AuthResult.Failure(AuthError(AuthErrorCode.Network))
        }

        val first = async { coordinator.currentUser() }
        val late = async { coordinator.currentUser() }
        runCurrent()
        assertEquals(AuthErrorCode.Network, assertIs<AuthResult.Failure>(first.await()).error.code)
        releaseLate401.complete(Unit)
        assertEquals(AuthErrorCode.Network, assertIs<AuthResult.Failure>(late.await()).error.code)

        assertEquals(1, rotations)
        assertSame(repository.first, store.saved)
    }

    @Test
    fun failedStorageRetriesTheAlreadyRotatedPairWithoutSendingTheOldTokenAgain() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        coordinator.authenticate(AuthCredentials.Login("+123", "fixture"))
        val next = repository.rotated()
        var rotations = 0
        repository.profile = { token ->
            if (token == next.accessToken) AuthResult.Success(repository.user) else denied()
        }
        repository.rotation = { rotations++; AuthResult.Success(next) }
        store.beforeWrite = { error("fixture failure") }

        val failed = assertIs<AuthResult.Failure>(coordinator.currentUser())
        assertEquals(AuthErrorCode.Storage, failed.error.code)
        assertSame(repository.first, store.saved)

        store.beforeWrite = {}
        assertIs<AuthResult.Success<AuthUser>>(coordinator.currentUser())
        assertSame(next, store.saved)
        assertEquals(1, rotations)
    }

    @Test
    fun startupRetainsTheSnapshotWithoutActivatingItWhenTheNetworkIsUnavailable() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore().apply { saved = repository.first }
        repository.profile = { AuthResult.Failure(AuthError(AuthErrorCode.Network)) }
        val coordinator = SessionCoordinator(repository, store)

        coordinator.restore()

        assertEquals(SessionStatus.VerificationUnavailable, coordinator.state.value)
        assertSame(repository.first, store.saved)
    }

    @Test
    fun terminalRefreshRejectionClearsTheSnapshot() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        coordinator.authenticate(AuthCredentials.Login("+123", "fixture"))
        repository.profile = { denied() }
        repository.rotation = { denied() }

        assertIs<AuthResult.Failure>(coordinator.currentUser())
        assertEquals(null, store.saved)
        assertEquals(SessionStatus.Guest, coordinator.state.value)
    }

    @Test
    fun mismatchedRefreshAccountKeepsTheSavedPairWithoutAnExtraRead() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val coordinator = SessionCoordinator(repository, store)
        coordinator.restore()
        coordinator.authenticate(AuthCredentials.Login("+123", "fixture"))
        val first = repository.first
        val wrong = AuthUser("different-account-id", "Анна", null, "+123", 21, null, first.expiresAt)
        val rotated = repository.rotated(wrong)
        repository.profile = { token ->
            if (token == rotated.accessToken) AuthResult.Success(repository.user) else denied()
        }
        repository.rotation = { AuthResult.Success(rotated) }
        store.beforeWrite = { error("A mismatched pair must never be written") }

        val failure = assertIs<AuthResult.Failure>(coordinator.currentUser())

        assertEquals(AuthErrorCode.UnexpectedResponse, failure.error.code)
        assertEquals(1, repository.profileReads)
        assertSame(first, store.saved)
        assertIs<SessionStatus.Active>(coordinator.state.value)
    }
}

internal fun denied() = AuthResult.Failure(AuthError(AuthErrorCode.SessionExpired))

internal fun SessionTestRepository.rotated(user: AuthUser = this.user) =
    AuthSession("new-fixture-access", first.expiresAt, "new-fixture-refresh", first.refreshExpiresAt, user)
