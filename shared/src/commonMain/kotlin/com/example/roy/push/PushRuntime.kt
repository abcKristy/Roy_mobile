package com.example.roy.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PushAuthorization { NotAsked, Denied, Allowed }

/** The platform owns the installation ID and never persists a raw provider token here. */
class PushRuntime(
    internal val installationId: String,
    internal val platform: String,
    initiallyRegistered: Boolean = false,
    private val persistRegistration: (Boolean) -> Unit = {},
    private val onAccountChanged: (String?) -> Unit = {},
    private val requestAuthorization: () -> Unit,
) {
    private val authorizationState = MutableStateFlow(PushAuthorization.NotAsked)
    private val tokenState = MutableStateFlow<String?>(null)
    val authorization = authorizationState.asStateFlow()
    internal val token = tokenState.asStateFlow()
    internal var hasRegistration = initiallyRegistered
        private set

    internal fun updateRegistration(registered: Boolean) {
        hasRegistration = registered
        persistRegistration(registered)
    }

    fun requestPermission() = requestAuthorization()

    internal fun updateAccount(userId: String?) = onAccountChanged(userId)

    fun updateAuthorization(authorization: PushAuthorization) {
        authorizationState.value = authorization
    }

    fun updateToken(token: String?) {
        tokenState.value = token?.takeIf { it.isNotBlank() && it.length <= 4096 }
    }
}
