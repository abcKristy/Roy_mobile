package com.example.roy.presentation.notifications

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.map.Position
import com.example.roy.domain.notifications.NotificationChannel
import com.example.roy.domain.notifications.NotificationRepository
import com.example.roy.domain.notifications.NotificationSettings
import com.example.roy.domain.notifications.NotificationSettingsWrite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
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
        val online = MutableStateFlow(true)
        val updates = mutableListOf<NotificationSettings>()
        val locations = mutableListOf<Position>()
        var readCount = 0

        suspend fun model(): NotificationViewModel {
            val auth = SessionTestRepository()
            val coordinator = SessionCoordinator(auth, SessionTestStore().apply { saved = auth.first })
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

        override suspend fun list(cursor: String?, token: String) = error("Unexpected list")
        override suspend fun markRead(id: String, token: String) = error("Unexpected markRead")

        override suspend fun setLocation(latitude: Double, longitude: Double, token: String): AuthResult<Unit> {
            locations += Position(latitude, longitude)
            return AuthResult.Success(Unit)
        }

        override suspend fun removeLocation(token: String): AuthResult<Unit> = AuthResult.Success(Unit)
    }
}
