package com.example.roy.presentation.navigation

import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.map.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthReturnCoordinatorTest {
    @Test
    fun initialExpiryCounterDoesNotOpenAuth() {
        val coordinator = AuthReturnCoordinator()

        assertNull(coordinator.onSessionExpired(0))
        assertNull(coordinator.currentAuthFlowId)
    }

    private val create = PendingProtectedIntent.CreatePoint(Position(55.75, 37.61))
    private val openTopic = PendingProtectedIntent.OpenTopic("point", "topic")
    private val active = SessionStatus.Active(1)

    @Test
    fun restoringDefersActionUntilSessionIsKnown() {
        val coordinator = AuthReturnCoordinator()
        assertNull(coordinator.request(create, SessionStatus.Restoring))
        assertNull(coordinator.onSessionChanged(SessionStatus.Restoring))

        val effect = coordinator.onSessionChanged(SessionStatus.Guest)
        assertIs<ProtectedNavigation.OpenAuth>(effect)
        assertNull(coordinator.onSessionChanged(SessionStatus.Guest))
    }

    @Test
    fun newActionAfterRestoreSupersedesEarlierActionBeforeStatusCollectorRuns() {
        val coordinator = AuthReturnCoordinator()
        assertNull(coordinator.request(create, SessionStatus.Restoring))
        assertEquals(
            ProtectedNavigation.Continue(openTopic, active.scopeId),
            coordinator.request(openTopic, active),
        )
        assertNull(coordinator.onSessionChanged(active))
    }

    @Test
    fun authorizedActionContinuesWithItsScope() {
        val coordinator = AuthReturnCoordinator()
        assertEquals(
            ProtectedNavigation.Continue(create, active.scopeId),
            coordinator.request(create, active),
        )
    }

    @Test
    fun latestGuestActionUsesOneAuthFlowAndContinuesOnceAfterSuccess() {
        val coordinator = AuthReturnCoordinator()
        val flow = assertIs<ProtectedNavigation.OpenAuth>(coordinator.request(create, SessionStatus.Guest))

        assertNull(coordinator.request(openTopic, SessionStatus.Guest))
        assertTrue(coordinator.isAuthOpen(flow.flowId))
        assertNull(coordinator.onSessionChanged(active))
        assertEquals(
            ProtectedNavigation.Continue(openTopic, active.scopeId),
            coordinator.authSucceeded(flow.flowId, active),
        )
        assertNull(coordinator.authSucceeded(flow.flowId, active))
        assertFalse(coordinator.isAuthOpen(flow.flowId))
    }

    @Test
    fun cancellationRejectsLateSuccessAndProfileLoginHasNoOldTarget() {
        val coordinator = AuthReturnCoordinator()
        val cancelled = assertIs<ProtectedNavigation.OpenAuth>(coordinator.request(openTopic, SessionStatus.Guest))
        assertTrue(coordinator.cancelAuth(cancelled.flowId))
        val profile = assertIs<ProtectedNavigation.OpenAuth>(coordinator.openAuth())

        assertNotEquals(cancelled.flowId, profile.flowId)
        assertNull(coordinator.authSucceeded(cancelled.flowId, active))
        assertFalse(coordinator.cancelAuth(cancelled.flowId))
        assertTrue(coordinator.isAuthOpen(profile.flowId))
        assertNull(coordinator.authSucceeded(profile.flowId, active))
        assertFalse(coordinator.isAuthOpen(profile.flowId))
    }

    @Test
    fun scopeChangeClearsPreviousIntent() {
        val coordinator = AuthReturnCoordinator()
        val flow = assertIs<ProtectedNavigation.OpenAuth>(coordinator.request(openTopic, SessionStatus.Guest))
        coordinator.onSessionChanged(active)
        coordinator.onSessionChanged(SessionStatus.Guest)

        assertFalse(coordinator.isAuthOpen(flow.flowId))
        assertNull(coordinator.authSucceeded(flow.flowId, SessionStatus.Active(2)))
    }

    @Test
    fun expiryOpensOneAuthFlowAndDoesNotKeepAbandonedTarget() {
        val coordinator = AuthReturnCoordinator()
        val previous = assertIs<ProtectedNavigation.OpenAuth>(coordinator.request(openTopic, SessionStatus.Guest))
        val expiry = assertIs<ProtectedNavigation.OpenAuth>(coordinator.onSessionExpired(10))

        assertNotEquals(previous.flowId, expiry.flowId)
        assertNull(coordinator.authSucceeded(previous.flowId, active))
        assertNull(coordinator.onSessionExpired(10))
        assertTrue(coordinator.cancelAuth(expiry.flowId))
        assertNull(coordinator.onSessionExpired(10))
        assertNull(coordinator.onSessionExpired(9))
    }

    @Test
    fun guestStatusAfterExpiryDoesNotCloseTheNewAuthFlow() {
        val coordinator = AuthReturnCoordinator()
        coordinator.onSessionChanged(active)
        val expiry = assertIs<ProtectedNavigation.OpenAuth>(coordinator.onSessionExpired(10))

        assertNull(coordinator.onSessionChanged(SessionStatus.Guest))
        assertTrue(coordinator.isAuthOpen(expiry.flowId))
    }

    @Test
    fun newOwnerDoesNotRestoreAnExecutableIntent() {
        val coordinator = AuthReturnCoordinator()
        val previous = assertIs<ProtectedNavigation.OpenAuth>(coordinator.request(create, SessionStatus.Guest))
        val restarted = AuthReturnCoordinator()

        assertNull(restarted.onSessionChanged(active))
        assertNull(restarted.authSucceeded(previous.flowId, active))
        assertFalse(restarted.isAuthOpen(previous.flowId))
    }

    @Test
    fun unavailableSessionDoesNotOpenProtectedNavigation() {
        val coordinator = AuthReturnCoordinator()
        assertNull(coordinator.request(create, SessionStatus.Restoring))
        assertNull(coordinator.onSessionChanged(SessionStatus.VerificationUnavailable))
        assertNull(coordinator.onSessionChanged(SessionStatus.Guest))
        assertNull(coordinator.request(openTopic, SessionStatus.StorageUnavailable))
    }
}
