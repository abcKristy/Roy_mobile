package com.example.roy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Debug-only, permission-protected entry into the same callbacks used by the provider. */
class DebugPushFixtureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.example.roy.debug.PUSH_TOKEN" -> RoyPush.runtime.updateToken(intent.getStringExtra("token"))
            "com.example.roy.debug.PUSH_MESSAGE" -> RoyPush.show(context, RoyPush.payload(intent))
        }
    }
}
