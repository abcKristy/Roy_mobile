package com.example.roy.presentation.notifications

import com.example.roy.domain.notifications.NotificationChannel

internal sealed class NotificationEvent {
    data class OnRefresh(val mode: NotificationMode) : NotificationEvent()
    data class OnChange(val channel: NotificationChannel, val enabled: Boolean) : NotificationEvent()
    data class OnOpen(val entryId: String) : NotificationEvent()
    data object OnMore : NotificationEvent()
    data object OnBack : NotificationEvent()
    data object OnRequestPermission : NotificationEvent()
    data class OnDismissPermission(val dismissed: Boolean) : NotificationEvent()
}
