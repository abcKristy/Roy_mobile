package com.example.roy.data.notifications

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthJson
import com.example.roy.data.auth.ErrorEnvelopeDto
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
                if (update.permission == PushAuthorization.Denied) {
                    if (native.hasRegistration) revoke(scope)
                } else if (update.permission == PushAuthorization.Allowed && token != null &&
                    (registeredScope != scope || token != registeredToken)
                ) {
                    val body = buildJsonObject {
                        put("platform", native.platform)
                        put("provider", if (native.platform == "android") "rustore" else "apns")
                        put("token", token)
                    }.toString()
                    val response = session.mutateProtected(scope) { access ->
                        val previous = native.hasRegistration
                        // Keep the marker if the outcome could have been committed before a response was lost.
                        native.updateRegistration(true)
                        val reply = transport.execute(AuthHttpRequest(AuthHttpMethod.Put, endpoint, body, access))
                        val code = reply?.let { runCatching { AuthJson.decodeFromString<ErrorEnvelopeDto>(it.body).error.code }.getOrNull() }
                        if (!previous && (reply?.status in setOf(400, 401, 403, 409, 422) ||
                                (reply?.status == 503 && code == "PUSH_REGISTRATION_FAILED"))) {
                            native.updateRegistration(false)
                        }
                        responseResult(reply?.status)
                    }
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
                val revoked = revoke(scope, allowMissingEndpoint = true)
                if (revoked is AuthResult.Failure) return@withLock revoked
            }
            session.logout(scope)
        } finally {
            if (session.state.value == SessionStatus.Active(scope)) {
                blockedScope = null
                reconcile.value++
            }
        }
    }

    private suspend fun revoke(scope: Long, allowMissingEndpoint: Boolean = false): AuthResult<Unit> {
        var endpointMissing = false
        suspend fun delete(access: String): AuthResult<Unit> {
            val status = transport.execute(AuthHttpRequest(AuthHttpMethod.Delete, endpoint, null, access))?.status
            endpointMissing = status == 404
            return responseResult(status)
        }
        var result = session.mutateProtected(scope, ::delete)
        // A rejected credential may have been refreshed. Revoking this installation is idempotent.
        if (result is AuthResult.Failure && result.error.code == AuthErrorCode.AuthRequired) {
            result = session.mutateProtected(scope, ::delete)
        }
        if (result is AuthResult.Success) {
            native.updateRegistration(false)
            registeredScope = null
            registeredToken = null
        }
        // When device registration is disabled, Ktor does not install the DELETE route.
        // Keep the persisted marker so a subsequent session can reconcile it if the route returns.
        return if (allowMissingEndpoint && endpointMissing && result is AuthResult.Failure &&
            result.error.code == AuthErrorCode.ServiceUnavailable
        ) AuthResult.Success(Unit) else result
    }

    private fun responseResult(status: Int?): AuthResult<Unit> = when (status) {
        200, 204 -> AuthResult.Success(Unit)
        401 -> AuthResult.Failure(AuthError(AuthErrorCode.AuthRequired))
        null -> AuthResult.Failure(AuthError(AuthErrorCode.Network))
        else -> AuthResult.Failure(AuthError(AuthErrorCode.ServiceUnavailable))
    }

    private class Registration(
        val scope: Long?,
        val permission: PushAuthorization,
        val token: String?,
        val online: Boolean,
    )
}
