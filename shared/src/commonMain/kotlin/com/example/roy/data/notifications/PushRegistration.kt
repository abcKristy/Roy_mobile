package com.example.roy.data.notifications

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.push.PushAuthorization
import com.example.roy.push.PushRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class PushRegistration(
    origin: String,
    private val transport: AuthHttpTransport,
    private val session: SessionCoordinator,
    private val native: PushRuntime,
) {
    private val endpoint = "${origin.trimEnd('/')}/api/v1/me/push-devices/${native.installationId}"
    private val gate = Mutex()
    private var blockedScope: Long? = null
    private var registeredScope: Long? = null
    private var registeredToken: String? = null
    private val reconcile = MutableStateFlow(0L)

    suspend fun observe(connectivity: Flow<Boolean>) {
        combine(session.state, native.authorization, native.token, connectivity, reconcile) { status, permission, token, online, _ ->
            Registration((status as? SessionStatus.Active)?.scopeId, permission, token, online)
        }.collect { update ->
            val scope = update.scope ?: return@collect
            if (!update.online) return@collect
            gate.withLock {
                if (blockedScope == scope || session.state.value != SessionStatus.Active(scope)) return@withLock
                val token = update.token
                if (update.permission != PushAuthorization.Allowed || token == null) {
                    if (registeredScope == scope) revoke(scope)
                } else if (registeredScope != scope || token != registeredToken) {
                    val body = buildJsonObject {
                        put("platform", native.platform)
                        put("provider", if (native.platform == "android") "rustore" else "apns")
                        put("token", token)
                    }.toString()
                    // A lost response may still leave an installation bound on the server.
                    native.updateRegistration(true)
                    val response = session.mutateProtected(scope) { request(AuthHttpMethod.Put, body, it) }
                    if (response is AuthResult.Success) {
                        registeredScope = scope
                        registeredToken = token
                    }
                }
            }
        }
    }

    suspend fun logout(scope: Long): AuthResult<Unit> = gate.withLock {
        if (session.state.value != SessionStatus.Active(scope)) {
            return@withLock AuthResult.Failure(AuthError(AuthErrorCode.SessionExpired))
        }
        blockedScope = scope
        try {
            if (native.hasRegistration) {
                val revoked = revoke(scope)
                if (revoked is AuthResult.Failure) return@withLock revoked
            }
            session.logout()
        } finally {
            if (session.state.value == SessionStatus.Active(scope)) {
                blockedScope = null
                reconcile.value++
            }
        }
    }

    private suspend fun revoke(scope: Long): AuthResult<Unit> {
        var result = session.mutateProtected(scope) { request(AuthHttpMethod.Delete, null, it) }
        // A rejected credential may have been refreshed. Revoking this installation is idempotent.
        if (result is AuthResult.Failure && result.error.code == AuthErrorCode.AuthRequired) {
            result = session.mutateProtected(scope) { request(AuthHttpMethod.Delete, null, it) }
        }
        if (result is AuthResult.Success) {
            native.updateRegistration(false)
            registeredScope = null
            registeredToken = null
        }
        return result
    }

    private suspend fun request(method: AuthHttpMethod, body: String?, access: String): AuthResult<Unit> {
        val response = transport.execute(AuthHttpRequest(method, endpoint, body, access))
        return when (response?.status) {
            200, 204 -> AuthResult.Success(Unit)
            401 -> AuthResult.Failure(AuthError(AuthErrorCode.AuthRequired))
            null -> AuthResult.Failure(AuthError(AuthErrorCode.Network))
            else -> AuthResult.Failure(AuthError(AuthErrorCode.ServiceUnavailable))
        }
    }

    private class Registration(
        val scope: Long?,
        val permission: PushAuthorization,
        val token: String?,
        val online: Boolean,
    )
}
