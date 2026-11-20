package com.example.roy

import android.Manifest
import android.app.Application
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.example.roy.domain.notifications.NotificationTarget
import com.example.roy.push.PushAuthorization
import com.example.roy.push.PushRuntime
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import ru.rustore.sdk.pushclient.RuStorePushClient
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService

class RoyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RoyPush.initialize(this)
        if (BuildConfig.RUSTORE_PROJECT_ID.isNotEmpty() && !RuStorePushClient.isInitialized) {
            RuStorePushClient.init(this, BuildConfig.RUSTORE_PROJECT_ID)
        }
    }
}

class RoyRuStoreService : RuStoreMessagingService() {
    override fun onNewToken(token: String) = RoyPush.updateProviderToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.notification == null) RoyPush.show(this, message.data)
    }
}

class NotificationTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && RoyPush.acceptTap(intent)) {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }
}

internal object RoyPush {
    private const val CHANNEL = "roy_meetings"
    private val payloadKeys = listOf("v", "kind", "notificationId", "pointId", "chatId", "proposalId", "recipientBinding")
    private val targets = Channel<NotificationTarget>(Channel.CONFLATED)
    val notificationTargets = targets.receiveAsFlow()
    private val accountLock = Any()
    private var accountBinding: String? = null
    private var tokenVersion = 0L
    private lateinit var app: Context
    private var requestPermission: (() -> Unit)? = null
    lateinit var runtime: PushRuntime
        private set

    fun initialize(context: Context) {
        app = context.applicationContext
        val file = File(app.noBackupFilesDir, "roy-installation-id")
        val id = runCatching { UUID.fromString(file.readText()).toString() }.getOrNull()
            ?: UUID.randomUUID().toString().also(file::writeText)
        val preferences = app.getSharedPreferences("roy_push", Context.MODE_PRIVATE)
        val account = File(app.noBackupFilesDir, "roy-push-binding")
        accountBinding = runCatching { account.readText() }.getOrNull()?.takeIf { it.length == 64 }
        runtime = PushRuntime(
            id, "android", initiallyRegistered = preferences.getString("registeredInstallation", null) == id,
            persistRegistration = {
                preferences.edit().putString("registeredInstallation", id.takeIf { _ -> it }).apply()
            },
            onAccountChanged = { userId ->
                val binding = userId?.let { recipientBinding(id, it) }
                synchronized(accountLock) {
                    if (accountBinding != binding) {
                        accountBinding = binding
                        runCatching { account.writeText(binding.orEmpty()) }
                        targets.tryReceive()
                        app.getSystemService(NotificationManager::class.java).cancelAll()
                    }
                }
            },
        ) { requestPermission?.invoke() }
        refreshPermission()
    }

    fun bindPermissionRequest(request: (() -> Unit)?) {
        requestPermission = request
    }

    fun markRequested() {
        app.getSharedPreferences("roy_push", Context.MODE_PRIVATE).edit().putBoolean("asked", true).apply()
    }

    fun refreshPermission() {
        val manager = app.getSystemService(NotificationManager::class.java)
        val allowed = manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val asked = app.getSharedPreferences("roy_push", Context.MODE_PRIVATE).getBoolean("asked", false)
        runtime.updateAuthorization(when {
            allowed -> PushAuthorization.Allowed
            asked || Build.VERSION.SDK_INT < 33 -> PushAuthorization.Denied
            else -> PushAuthorization.NotAsked
        })
        if (allowed && RuStorePushClient.isInitialized) {
            val version = synchronized(accountLock) { ++tokenVersion }
            RuStorePushClient.getToken().addOnSuccessListener { token ->
                synchronized(accountLock) {
                    if (version == tokenVersion) runtime.updateToken(token)
                }
            }
        }
    }

    fun updateProviderToken(token: String?) {
        synchronized(accountLock) {
            tokenVersion++
            runtime.updateToken(token)
        }
    }

    fun payload(intent: Intent): Map<String, String> = buildMap {
        payloadKeys.forEach { key -> intent.getStringExtra(key)?.let { put(key, it) } }
    }

    fun target(data: Map<String, String>) = NotificationTarget.fromPush(
        data["v"], data["kind"], data["notificationId"], data["pointId"], data["chatId"], data["proposalId"],
    )

    fun acceptTap(intent: Intent): Boolean = synchronized(accountLock) {
        val data = payload(intent)
        val target = ownedTarget(data) ?: return@synchronized false
        targets.trySend(target).isSuccess
    }

    private fun ownedTarget(data: Map<String, String>): NotificationTarget? {
        if (accountBinding == null || data["recipientBinding"] != accountBinding) return null
        return target(data)
    }

    private fun recipientBinding(installationId: String, userId: String): String =
        MessageDigest.getInstance("SHA-256").digest("$installationId:$userId".encodeToByteArray()).toHexString()

    fun show(context: Context, data: Map<String, String>) = synchronized(accountLock) {
        val target = ownedTarget(data) ?: return@synchronized
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        ) return@synchronized
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, context.getString(R.string.roy_push_channel), NotificationManager.IMPORTANCE_DEFAULT,
        ))
        val open = Intent(context, NotificationTapActivity::class.java)
            .setData(Uri.parse("roy://notification/${target.notificationId}"))
        payloadKeys.forEach { key -> data[key]?.let { open.putExtra(key, it) } }
        val content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (data["kind"] == "reminder_1h") R.string.roy_push_reminder else R.string.roy_push_generic
        manager.notify(target.notificationId, 1, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.roy_notification_icon)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(text))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build())
    }
}
