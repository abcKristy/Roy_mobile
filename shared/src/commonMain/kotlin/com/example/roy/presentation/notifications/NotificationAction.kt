package com.example.roy.presentation.notifications

import com.example.roy.domain.notifications.NotificationTarget

internal sealed class NotificationAction {
    data class Open(val target: NotificationTarget) : NotificationAction()
    data object Back : NotificationAction()
    data object RequestPermission : NotificationAction()
}
