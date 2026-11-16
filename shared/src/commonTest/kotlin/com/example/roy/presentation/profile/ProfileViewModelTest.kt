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
            assertFalse(model.viewState.value.isDeletionUncertain)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class Fixture(
        val model: ProfileViewModel,
        val repository: Repository,
        val online: MutableStateFlow<Boolean>,
    ) {
        companion object {
            suspend fun create(): Fixture {
                val session = SessionCoordinator(SessionTestRepository(), SessionTestStore())
                session.restore()
                session.authenticate(AuthCredentials.Login("+123", "fixture"))
                val scope = (session.state.value as SessionStatus.Active).scopeId
                val current = requireNotNull(session.cachedUser(scope))
                val repository = Repository(AuthUser(
                    current.id, current.displayName, "/api/v1/users/${current.id}/avatar",
                    current.phone, current.ageYears, current.messageMutedUntil, current.createdAt,
                ))
                val online = MutableStateFlow(true)
                return Fixture(ProfileViewModel(scope, session, repository, online), repository, online)
            }
        }
    }

    private class Repository(private val user: AuthUser) : ProfileRepository {
        var profileReads = 0
        var uploadCount = 0
        var deletionCount = 0

        override suspend fun getProfile(token: String): AuthResult<ProfileResult<AuthUser>> {
            profileReads++
            return AuthResult.Success(ProfileResult.Value(user))
        }

        override suspend fun updateName(name: String, token: String): AuthResult<ProfileResult<AuthUser>> = error("Unexpected save")

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
