package com.example.roy.push

import com.example.roy.domain.notifications.NotificationTarget
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSRecursiveLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
object IosPush {
    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private val targets = Channel<NotificationTarget>(Channel.CONFLATED)
    internal val notificationTargets = targets.receiveAsFlow()
    private val preferences = NSUserDefaults.standardUserDefaults
    private val accountLock = NSRecursiveLock()
    private val installation = installationId()
    private var accountBinding = preferences.stringForKey("royPushAccountBinding")?.takeIf { it.length == 64 }
    internal val runtime = PushRuntime(
        installation, "ios", initiallyRegistered = preferences.boolForKey("royPushRegistration"),
        persistRegistration = { preferences.setBool(it, forKey = "royPushRegistration") },
        onAccountChanged = ::updateAccount,
        requestAuthorization = ::requestPermission,
    )

    fun refreshPermission() {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional -> PushAuthorization.Allowed
                UNAuthorizationStatusNotDetermined, null -> PushAuthorization.NotAsked
                else -> PushAuthorization.Denied
            }
            runtime.updateAuthorization(status)
            if (status == PushAuthorization.Allowed) register()
        }
    }

    fun receiveToken(token: String): Unit = runtime.updateToken(token)

    fun receiveNotification(
        version: String?, kind: String?, notificationId: String?, pointId: String?, chatId: String?, proposalId: String?,
        recipientBinding: String?,
    ): Unit = withAccountLock {
        if (accountBinding == null || recipientBinding != accountBinding) return@withAccountLock
        NotificationTarget.fromPush(version, kind, notificationId, pointId, chatId, proposalId)
            ?.let(targets::trySend)
        Unit
    }

    private fun updateAccount(userId: String?) = withAccountLock {
        val binding = userId?.let { iosRecipientBinding(installation, it) }
        if (accountBinding != binding) {
            accountBinding = binding
            preferences.setObject(binding, forKey = "royPushAccountBinding")
            targets.tryReceive()
            center.removeAllDeliveredNotifications()
            center.removeAllPendingNotificationRequests()
        }
    }

    private inline fun <T> withAccountLock(block: () -> T): T {
        accountLock.lock()
        return try { block() } finally { accountLock.unlock() }
    }

    private fun requestPermission() {
        if (runtime.authorization.value == PushAuthorization.Denied) {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
            }
            return
        }
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
        center.requestAuthorizationWithOptions(options) { _, _ -> refreshPermission() }
    }

    private fun register() {
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.registerForRemoteNotifications()
        }
    }

    private fun installationId(): String {
        val preferences = NSUserDefaults.standardUserDefaults
        val key = "royInstallationId"
        return preferences.stringForKey(key) ?: NSUUID().UUIDString.lowercase().also { preferences.setObject(it, forKey = key) }
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
internal fun iosRecipientBinding(installationId: String, userId: String): String {
    val input = "$installationId:$userId".encodeToByteArray()
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { source ->
        digest.usePinned { target -> CC_SHA256(source.addressOf(0), input.size.convert(), target.addressOf(0)) }
    }
    return digest.toByteArray().toHexString()
}
