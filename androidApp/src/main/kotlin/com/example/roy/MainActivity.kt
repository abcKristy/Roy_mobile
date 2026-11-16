package com.example.roy

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.roy.shared.RoyAndroidApp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    private val meetingTargets = Channel<MeetingNotificationTarget>(Channel.CONFLATED)
    private val meetingTargetFlow = meetingTargets.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) receiveMeetingTarget(intent)

        setContent {
            RoyAndroidApp(applicationContext, apiOrigin(), meetingTargetFlow)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveMeetingTarget(intent)
    }

    private fun receiveMeetingTarget(intent: Intent?) {
        if (intent == null) return
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
