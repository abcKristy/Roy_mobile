package com.example.roy.push

import com.example.roy.domain.notifications.NotificationTarget
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

object IosPush {
    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private val targets = Channel<NotificationTarget>(Channel.CONFLATED)
    internal val notificationTargets = targets.receiveAsFlow()
    private val preferences = NSUserDefaults.standardUserDefaults
    internal val runtime = PushRuntime(
        installationId(), "ios", initiallyRegistered = preferences.boolForKey("royPushRegistration"),
        persistRegistration = { preferences.setBool(it, forKey = "royPushRegistration") },
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

    fun receiveToken(token: String) = runtime.updateToken(token)

    fun receiveNotification(
        version: String?, kind: String?, notificationId: String?, pointId: String?, chatId: String?, proposalId: String?,
    ) {
        NotificationTarget.fromPush(version, kind, notificationId, pointId, chatId, proposalId)
            ?.let(targets::trySend)
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
