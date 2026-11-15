package com.example.roy.presentation.navigation

import com.example.roy.domain.auth.SessionStatus

internal sealed interface ProtectedNavigation {
    data class OpenAuth(val flowId: Long) : ProtectedNavigation
    data class Continue(val intent: PendingProtectedIntent, val scopeId: Long) : ProtectedNavigation
}

/** Memory-only owner. Call from the navigation owner's UI context. */
internal class AuthReturnCoordinator {
    private data class AuthFlow(val id: Long, val intent: PendingProtectedIntent?)

    private var flow: AuthFlow? = null
    private var waitingForSession: PendingProtectedIntent? = null
    private var nextFlowId = 0L
    private var activeScopeId: Long? = null
    private var handledExpiryId: Long? = null

    val currentAuthFlowId: Long? get() = flow?.id

    fun request(intent: PendingProtectedIntent, session: SessionStatus): ProtectedNavigation? {
        updateSessionScope(session)
        if (session != SessionStatus.Restoring) waitingForSession = null
        return when (session) {
            SessionStatus.Restoring -> {
                waitingForSession = intent
                null
            }
            SessionStatus.Guest -> {
                val current = flow
                if (current == null) {
                    beginAuth(intent)
                } else {
                    flow = current.copy(intent = intent)
                    null
                }
            }
            is SessionStatus.Active -> ProtectedNavigation.Continue(intent, session.scopeId)
            SessionStatus.StorageUnavailable, SessionStatus.VerificationUnavailable -> null
        }
    }

    fun onSessionChanged(session: SessionStatus): ProtectedNavigation? {
        updateSessionScope(session)
        if (session == SessionStatus.Restoring) return null
        val intent = waitingForSession
        waitingForSession = null
        return intent?.let { request(it, session) }
    }

    fun openAuth(): ProtectedNavigation? = if (flow == null) beginAuth(null) else null

    fun onSessionExpired(expiryId: Long): ProtectedNavigation? {
        if (expiryId <= 0L || handledExpiryId?.let { expiryId <= it } == true) return null
        handledExpiryId = expiryId
        activeScopeId = null
        return beginAuth(null)
    }

    fun isAuthOpen(flowId: Long): Boolean = flow?.id == flowId

    fun authSucceeded(flowId: Long, session: SessionStatus.Active): ProtectedNavigation? {
        val completed = flow?.takeIf { it.id == flowId } ?: return null
        flow = null
        activeScopeId = session.scopeId
        return completed.intent?.let { ProtectedNavigation.Continue(it, session.scopeId) }
    }

    fun cancelAuth(flowId: Long): Boolean {
        if (!isAuthOpen(flowId)) return false
        flow = null
        return true
    }

    private fun beginAuth(intent: PendingProtectedIntent?): ProtectedNavigation.OpenAuth {
        waitingForSession = null
        val flowId = ++nextFlowId
        flow = AuthFlow(flowId, intent)
        return ProtectedNavigation.OpenAuth(flowId)
    }

    private fun updateSessionScope(session: SessionStatus) {
        val scope = (session as? SessionStatus.Active)?.scopeId
        if (activeScopeId != null && activeScopeId != scope) {
            flow = null
            waitingForSession = null
        }
        activeScopeId = scope
    }
}
