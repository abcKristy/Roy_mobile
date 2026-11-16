package com.example.roy

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.roy.domain.notifications.NotificationTarget
import com.example.roy.shared.RoyAndroidApp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    private val meetingTargets = Channel<MeetingNotificationTarget>(Channel.CONFLATED)
    private val meetingTargetFlow = meetingTargets.receiveAsFlow()
    private val notificationTargets = Channel<NotificationTarget>(Channel.CONFLATED)
    private val notificationTargetFlow = notificationTargets.receiveAsFlow()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        RoyPush.refreshPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) receiveMeetingTarget(intent)

        setContent {
            RoyAndroidApp(applicationContext, apiOrigin(), meetingTargetFlow, notificationTargetFlow, RoyPush.runtime)
        }
    }

    override fun onResume() {
        super.onResume()
        RoyPush.bindPermissionRequest(::requestPushPermission)
        RoyPush.refreshPermission()
    }

    override fun onPause() {
        RoyPush.bindPermissionRequest(null)
        super.onPause()
    }

    private fun requestPushPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            (RoyPush.runtime.authorization.value == com.example.roy.push.PushAuthorization.NotAsked ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            RoyPush.markRequested()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveMeetingTarget(intent)
    }

    private fun receiveMeetingTarget(intent: Intent?) {
        if (intent == null) return
        val notification = RoyPush.target(RoyPush.payload(intent))
        if (notification != null) {
            notificationTargets.trySend(notification)
            return
        }
        val target = MeetingNotificationTarget.fromPayload(
            intent.getStringExtra("v"), intent.getStringExtra("kind"), intent.getStringExtra("pointId"),
            intent.getStringExtra("chatId"), intent.getStringExtra("proposalId"),
        )
        target?.let(meetingTargets::trySend)
    }

    private fun apiOrigin(): String? =
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "http://127.0.0.1:18080"
        } else {
            null
        }
}
