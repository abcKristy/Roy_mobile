package com.example.roy.presentation.notifications

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthCredentials
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthRepository
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthSession
import com.example.roy.domain.auth.AuthUser
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.map.Position
import com.example.roy.domain.notifications.NotificationChannel
import com.example.roy.domain.notifications.NotificationEntry
import com.example.roy.domain.notifications.NotificationPage
import com.example.roy.domain.notifications.NotificationRepository
import com.example.roy.domain.notifications.NotificationSettings
import com.example.roy.domain.notifications.NotificationSettingsWrite
import com.example.roy.domain.notifications.NotificationTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    @Test
    fun confirmationReturnsServerTargetAndUpdatesTheLoadedNotification() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val model = fixture.model()
        try {
            runCurrent()
            model.handleEvent(NotificationEvent.Refresh(NotificationMode.Center))
            runCurrent()
            val confirmed = fixture.entry.copy(readAt = fixture.entry.createdAt, target = NotificationTarget.fromPush(
                "1", "nearby_meeting", fixture.entry.id, "00000000-0000-0000-0000-000000000004", null, null,
            ))
            fixture.confirm = { AuthResult.Success(confirmed) }

            val result = assertIs<AuthResult.Success<NotificationEntry>>(model.confirmNotification(fixture.entry.id))

            assertEquals(confirmed, result.value)
            assertEquals(listOf(confirmed), model.viewState.value.items)
            assertEquals(listOf(fixture.entry.id), fixture.confirmedIds)
            assertNotNull(result.value.target)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rejectedConfirmationPreservesUnreadNotificationAndReturnsNoTarget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val model = fixture.model()
        try {
            runCurrent()
            model.handleEvent(NotificationEvent.Refresh(NotificationMode.Center))
            runCurrent()
            val rejected = AuthResult.Failure(AuthError(AuthErrorCode.ServiceUnavailable))
            fixture.confirm = { rejected }

            assertEquals(rejected, model.confirmNotification(fixture.entry.id))
            assertEquals(listOf(fixture.entry), model.viewState.value.items)
            assertEquals(listOf(fixture.entry.id), fixture.confirmedIds)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun delayedConfirmationFromPreviousAccountCannotReturnItsTargetOrRestoreItsItems() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val model = fixture.model()
        var nextModel: NotificationViewModel? = null
        try {
            runCurrent()
            model.handleEvent(NotificationEvent.Refresh(NotificationMode.Center))
            runCurrent()
            assertEquals(listOf(fixture.entry), model.viewState.value.items)
            val reply = CompletableDeferred<AuthResult<NotificationEntry>>()
            fixture.confirm = { reply.await() }
            val pending = async { model.confirmNotification(fixture.entry.id) }
            runCurrent()
            assertEquals(listOf(fixture.entry.id), fixture.confirmedIds)

            assertIs<AuthResult.Success<Unit>>(fixture.coordinator.logout())
            runCurrent()
            assertEquals(emptyList(), model.viewState.value.items)
            val original = fixture.loginSession
            fixture.loginSession = AuthSession(
                "other-access", original.expiresAt, "other-refresh", original.refreshExpiresAt,
                AuthUser("other-user", "Other", null, "+456", 22, null, original.user.createdAt),
            )
            assertIs<AuthResult.Success<Unit>>(fixture.coordinator.authenticate(AuthCredentials.Login("+456", "fixture")))
            nextModel = fixture.model()
            runCurrent()
            reply.complete(AuthResult.Success(fixture.entry.copy(readAt = fixture.entry.createdAt)))

            val result = assertIs<AuthResult.Failure>(pending.await())
            assertEquals(AuthErrorCode.SessionExpired, result.error.code)
            assertEquals(emptyList(), model.viewState.value.items)
            assertEquals(emptyList(), nextModel.viewState.value.items)
            assertEquals("other-user", fixture.coordinator.cachedUser(
                assertIs<SessionStatus.Active>(fixture.coordinator.state.value).scopeId,
            )?.id)
        } finally {
            nextModel?.viewModelScope?.cancel()
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun settingsConflictReloadsWithoutRepeatingTheWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val model = fixture.model()
        try {
            runCurrent()
            model.handleEvent(NotificationEvent.Change(NotificationChannel.PersonalPush, false))
            runCurrent()
            assertEquals(1, fixture.updates.size)
            assertEquals(0L, fixture.updates.single().revision)
            assertEquals(2, fixture.readCount)
            assertEquals(1L, model.viewState.value.settings?.revision)
            assertEquals(NotificationIssue.Conflict, model.viewState.value.settingsIssue)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun viewportSendsLatestOptedInCenterAndDropsOfflineWork() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val model = fixture.model()
        try {
            runCurrent()
            model.onViewportSettled(Position(52.1, 20.1))
            advanceTimeBy(250)
            model.onViewportSettled(Position(52.2, 20.2))
            advanceTimeBy(501)
            assertEquals(listOf(Position(52.2, 20.2)), fixture.locations)
            model.onViewportSettled(Position(52.3, 20.3))
            fixture.online.value = false
            runCurrent()
            model.onViewportSettled(Position(52.4, 20.4))
            fixture.online.value = true
            runCurrent()
            advanceTimeBy(1_000)
            assertEquals(listOf(Position(52.2, 20.2)), fixture.locations)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class Fixture : NotificationRepository {
        private val auth = SessionTestRepository().apply { revoke = { AuthResult.Success(Unit) } }
        var loginSession = auth.first
        val coordinator = SessionCoordinator(object : AuthRepository by auth {
            override suspend fun login(credentials: AuthCredentials.Login) = AuthResult.Success(loginSession)
        }, SessionTestStore().apply { saved = auth.first })
        val online = MutableStateFlow(true)
        val updates = mutableListOf<NotificationSettings>()
        val locations = mutableListOf<Position>()
        var readCount = 0
        val confirmedIds = mutableListOf<String>()
        val entry = NotificationEntry(
            "00000000-0000-0000-0000-000000000001", "nearby_meeting", "Meeting", "Synthetic notification",
            NotificationTarget.fromPush("1", "nearby_meeting", "00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000002", null, null),
            Instant.parse("2026-11-20T12:00:00Z"), null,
        )
        var confirm: suspend (String) -> AuthResult<NotificationEntry> = { error("Unexpected markRead") }

        suspend fun model(): NotificationViewModel {
            coordinator.restore()
            val scope = (coordinator.state.value as SessionStatus.Active).scopeId
            return NotificationViewModel(scope, coordinator, this, online)
        }

        override suspend fun settings(token: String): AuthResult<NotificationSettings> =
            AuthResult.Success(NotificationSettings(false, true, true, false, (readCount++).toLong()))

        override suspend fun update(value: NotificationSettings, token: String): AuthResult<NotificationSettingsWrite> {
            updates += value
            return AuthResult.Success(NotificationSettingsWrite.Conflict(null))
        }

        override suspend fun list(cursor: String?, token: String) = AuthResult.Success(NotificationPage(listOf(entry), null))
        override suspend fun markRead(id: String, token: String): AuthResult<NotificationEntry> {
            confirmedIds += id
            return confirm(id)
        }

        override suspend fun setLocation(latitude: Double, longitude: Double, token: String): AuthResult<Unit> {
            locations += Position(latitude, longitude)
            return AuthResult.Success(Unit)
        }

        override suspend fun removeLocation(token: String): AuthResult<Unit> = AuthResult.Success(Unit)
    }
}
