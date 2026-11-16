package com.example.roy.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class SessionCoordinatorTest {
    @Test
    fun publishesAnActiveSessionOnlyAfterThePairIsWritten() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val session = SessionCoordinator(repository, store)
        session.restore()
        store.beforeWrite = { assertEquals(SessionStatus.Guest, session.state.value) }

        assertIs<AuthResult.Success<Unit>>(session.authenticate(AuthCredentials.Login("+123", "fixture")))

        assertSame(repository.first, store.saved)
        assertIs<SessionStatus.Active>(session.state.value)
    }

    @Test
    fun failedWriteKeepsTheUserInGuest() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore()
        val session = SessionCoordinator(repository, store)
        session.restore()
        store.beforeWrite = { error("fixture storage failure") }

        val failure = assertIs<AuthResult.Failure>(
            session.authenticate(AuthCredentials.Login("+123", "fixture")),
        )

        assertEquals(AuthErrorCode.Storage, failure.error.code)
        assertEquals(SessionStatus.Guest, session.state.value)
        assertEquals(null, store.saved)
    }

    @Test
    fun restoresAStoredSessionThroughTheProtectedProfile() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore().apply { saved = repository.first }
        val session = SessionCoordinator(repository, store)

        session.restore()

        assertIs<SessionStatus.Active>(session.state.value)
        assertEquals(repository.first.user.id, store.saved?.user?.id)
        assertEquals(1, repository.profileReads)
    }

    @Test
    fun offlineColdStartExposesOnlyCachedIdentityUntilVerifiedAndClearsOnOfflineLogout() = runTest {
        val repository = SessionTestRepository()
        val store = SessionTestStore().apply { saved = repository.first }
        var purges = 0
        var mutations = 0
        val session = SessionCoordinator(repository, store, { purges++ }, allowOfflineRestore = true)

        session.setOnline(false)
        session.restore()
        val scope = assertIs<SessionStatus.Active>(session.state.value).scopeId
        assertEquals(repository.user.id, session.cachedUser(scope)?.id)
        assertEquals(0, repository.profileReads)
        val rejected = session.mutateProtected(scope) { mutations++; AuthResult.Success(Unit) }
        assertEquals(AuthErrorCode.Network, assertIs<AuthResult.Failure>(rejected).error.code)
        assertEquals(0, mutations)

        session.setOnline(true)
        assertEquals(1, repository.profileReads)
        assertIs<AuthResult.Success<Unit>>(session.mutateProtected(scope) { mutations++; AuthResult.Success(Unit) })
        assertEquals(1, mutations)

        session.setOnline(false)
        assertIs<AuthResult.Success<Unit>>(session.logout())
        assertEquals(SessionStatus.Guest, session.state.value)
        assertEquals(null, session.cachedUser(scope))
        assertEquals(null, store.saved)
        assertEquals(1, purges)
    }
}

internal class SessionTestStore : SessionStore {
    var saved: AuthSession? = null
    var beforeWrite: () -> Unit = {}
    override suspend fun read() = saved
    override suspend fun replace(session: AuthSession) {
        beforeWrite()
        saved = session
    }
    override suspend fun clear() {
        saved = null
    }
}

internal class SessionTestRepository : AuthRepository {
    private val expiry = Instant.parse("2030-01-01T12:00:00Z")
    val user = AuthUser("fixture-user", "Анна", null, "+123", 21, null, expiry)
    val first = AuthSession("fixture-access", expiry, "fixture-refresh", expiry, user)
    var profileReads = 0
    var profile: suspend (String) -> AuthResult<AuthUser> = { AuthResult.Success(user) }
    var rotation: suspend (String) -> AuthResult<AuthSession> = { error("Unexpected refresh") }
    var revoke: suspend (String) -> AuthResult<Unit> = { error("Unexpected logout") }

    override suspend fun login(credentials: AuthCredentials.Login) = AuthResult.Success(first)
    override suspend fun register(credentials: AuthCredentials.Register) = AuthResult.Success(first)
    override suspend fun refresh(refreshToken: String) = rotation(refreshToken)
    override suspend fun currentUser(accessToken: String): AuthResult<AuthUser> {
        profileReads++
        return profile(accessToken)
    }
    override suspend fun logout(accessToken: String) = revoke(accessToken)
    override suspend fun privacyPolicy() = AuthResult.Success(PrivacyPolicy("fixture", expiry, "https://example.test/policy"))
}
