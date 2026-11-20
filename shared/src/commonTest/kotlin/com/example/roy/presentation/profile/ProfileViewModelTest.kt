package com.example.roy.presentation.profile

import com.example.roy.domain.auth.AuthCredentials
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthUser
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.profile.PickImageResult
import com.example.roy.domain.profile.ProfileAvatar
import com.example.roy.domain.profile.ProfileProblem
import com.example.roy.domain.profile.ProfileRepository
import com.example.roy.domain.profile.ProfileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @Test
    fun cancelledAvatarPickerDoesNotUploadAndKeepsCanonicalPhoto() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture.create()
            fixture.model.handleEvent(ProfileViewEvent.Load)
            runCurrent()
            val avatar = fixture.model.viewState.value.avatar
            fixture.model.handleEvent(ProfileViewEvent.PickAvatarRequested)
            assertTrue(fixture.model.viewState.value.isPickerPending)
            fixture.model.handleEvent(ProfileViewEvent.AvatarPicked(PickImageResult.Cancelled))
            assertFalse(fixture.model.viewState.value.isPickerPending)
            assertEquals(avatar, fixture.model.viewState.value.avatar)
            assertEquals(0, fixture.repository.uploadCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deletionTimeoutBlocksReplayAndVerificationUsesReadOnly() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture.create()
            val model = fixture.model
            model.handleEvent(ProfileViewEvent.Load)
            runCurrent()
            model.handleEvent(ProfileViewEvent.DeleteAccountRequested)
            model.handleEvent(ProfileViewEvent.DeletionPasswordChanged("fixture"))
            model.handleEvent(ProfileViewEvent.DialogConfirmed)
            model.handleEvent(ProfileViewEvent.DialogConfirmed)
            runCurrent()
            assertTrue(model.viewState.value.isDeletionUncertain)
            assertEquals("", model.viewState.value.deletionPassword)
            fixture.online.value = false
            runCurrent()
            fixture.online.value = true
            runCurrent()
            model.handleEvent(ProfileViewEvent.DialogConfirmed)
            runCurrent()
            assertEquals(1, fixture.repository.deletionCount)
            model.handleEvent(ProfileViewEvent.CheckDeletion)
            runCurrent()
            assertEquals(2, fixture.repository.profileReads)
            assertEquals(1, fixture.repository.deletionCount)
            assertTrue(model.viewState.value.isDeletionUncertain)
            assertFalse(model.viewState.value.canSubmit)
            assertEquals(ProfileNotice.DeletionCheckAvailable, model.viewState.value.notice)
            model.handleEvent(ProfileViewEvent.DialogDismissed)
            model.handleEvent(ProfileViewEvent.LogoutRequested)
            assertEquals(ProfileDialog.Logout, model.viewState.value.dialog)
            model.handleEvent(ProfileViewEvent.DialogConfirmed)
            runCurrent()
            assertEquals(SessionStatus.Guest, fixture.session.state.value)
            assertEquals(1, fixture.repository.deletionCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun canonicalNameSurvivesOfflineProfileRestart() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture.create()
            fixture.model.handleEvent(ProfileViewEvent.Load)
            runCurrent()
            fixture.model.handleEvent(ProfileViewEvent.NameChanged("Новое имя"))
            fixture.model.handleEvent(ProfileViewEvent.SaveName)
            runCurrent()
            val restarted = SessionCoordinator(SessionTestRepository(), fixture.store, allowOfflineRestore = true)
            restarted.setOnline(false)
            restarted.restore()
            val scope = (restarted.state.value as SessionStatus.Active).scopeId
            val offline = ProfileViewModel(scope, restarted, fixture.repository, MutableStateFlow(false))
            runCurrent()
            offline.handleEvent(ProfileViewEvent.Load)
            runCurrent()
            assertEquals("Новое имя", offline.viewState.value.user?.displayName)
            assertTrue(offline.viewState.value.isCached)
            assertEquals(1, fixture.repository.profileReads)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun populatedProfileRefreshesOnReconnectAndOnlyAllowsEditsAfterSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture.create()
            val model = fixture.model
            model.handleEvent(ProfileViewEvent.Load)
            runCurrent()
            assertTrue(model.viewState.value.canSubmit)
            fixture.online.value = false
            runCurrent()
            assertTrue(model.viewState.value.isCached)
            fixture.repository.isUnavailable = true
            fixture.online.value = true
            runCurrent()
            assertEquals(2, fixture.repository.profileReads)
            assertEquals(ProfileProblem.Unavailable, model.viewState.value.error)
            assertFalse(model.viewState.value.canSubmit)
            fixture.repository.isUnavailable = false
            model.handleEvent(ProfileViewEvent.Load)
            runCurrent()
            assertEquals(3, fixture.repository.profileReads)
            assertTrue(model.viewState.value.canSubmit)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class Fixture(
        val model: ProfileViewModel,
        val repository: Repository,
        val online: MutableStateFlow<Boolean>,
        val session: SessionCoordinator,
        val store: SessionTestStore,
    ) {
        companion object {
            suspend fun create(): Fixture {
                val store = SessionTestStore()
                val auth = SessionTestRepository().apply { revoke = { AuthResult.Success(Unit) } }
                val session = SessionCoordinator(auth, store)
                session.restore()
                session.authenticate(AuthCredentials.Login("+123", "fixture"))
                val scope = (session.state.value as SessionStatus.Active).scopeId
                val current = requireNotNull(session.cachedUser(scope))
                val repository = Repository(AuthUser(
                    current.id, current.displayName, "/api/v1/users/${current.id}/avatar",
                    current.phone, current.ageYears, current.messageMutedUntil, current.createdAt,
                ))
                val online = MutableStateFlow(true)
                return Fixture(ProfileViewModel(scope, session, repository, online), repository, online, session, store)
            }
        }
    }

    private class Repository(private var user: AuthUser) : ProfileRepository {
        var profileReads = 0
        var uploadCount = 0
        var deletionCount = 0
        var isUnavailable = false

        override suspend fun getProfile(token: String): AuthResult<ProfileResult<AuthUser>> {
            profileReads++
            if (isUnavailable) return AuthResult.Success(ProfileResult.Rejected(ProfileProblem.Unavailable))
            return AuthResult.Success(ProfileResult.Value(user))
        }

        override suspend fun updateName(name: String, token: String): AuthResult<ProfileResult<AuthUser>> {
            user = AuthUser(user.id, name, user.avatarUrl, user.phone, user.ageYears, user.messageMutedUntil, user.createdAt)
            return AuthResult.Success(ProfileResult.Value(user))
        }

        override suspend fun uploadAvatar(image: PickImageResult.Selected, token: String): AuthResult<ProfileResult<String>> {
            uploadCount++
            return AuthResult.Success(ProfileResult.Value(requireNotNull(user.avatarUrl)))
        }

        override suspend fun readAvatar(userId: String, etag: String?, token: String): AuthResult<ProfileResult<ProfileAvatar>> =
            AuthResult.Success(ProfileResult.Value(ProfileAvatar.Image(byteArrayOf(1), "image/png", "tag")))

        override suspend fun deleteAvatar(token: String): AuthResult<ProfileResult<Unit>> = error("Unexpected removal")

        override suspend fun changePassword(current: String, replacement: String, token: String): AuthResult<ProfileResult<Unit>> =
            error("Unexpected password change")

        override suspend fun deleteAccount(current: String, confirm: Boolean, token: String): AuthResult<ProfileResult<Unit>> {
            deletionCount++
            return AuthResult.Success(ProfileResult.Rejected(ProfileProblem.DeletionUncertain))
        }
    }
}
