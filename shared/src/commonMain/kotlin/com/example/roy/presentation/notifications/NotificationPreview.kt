package com.example.roy.presentation.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.notifications.NotificationEntry
import com.example.roy.domain.notifications.NotificationSettings
import com.example.roy.domain.notifications.PushPermission
import com.example.roy.presentation.theme.RoyTheme
import kotlin.time.Instant

@Preview(name = "Настройки уведомлений — светлая тема")
@Composable
private fun NotificationSettingsPreview() {
    RoyTheme(darkTheme = false) {
        NotificationView(
            NotificationMode.Settings,
            NotificationViewState(NotificationSettings(true, true, false, true, 1), online = true, settingsLoading = false),
            PushPermission.NotAsked,
            eventHandler = {},
        )
    }
}

@Preview(name = "Центр уведомлений — тёмная тема")
@Composable
private fun NotificationCenterPreview() {
    val entry = NotificationEntry(
        "preview", "reminder_1h", "Напоминание о встрече", "Бег в городском парке",
        null, Instant.parse("2026-11-15T11:00:00Z"), null,
    )
    RoyTheme(darkTheme = true) {
        NotificationView(
            NotificationMode.Center, NotificationViewState(items = listOf(entry), online = true, centerLoading = false),
            PushPermission.Allowed,
            eventHandler = {},
        )
    }
}
