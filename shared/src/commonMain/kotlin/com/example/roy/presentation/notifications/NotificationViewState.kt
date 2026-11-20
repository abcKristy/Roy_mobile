package com.example.roy.presentation.notifications

import com.example.roy.domain.notifications.NotificationChannel
import com.example.roy.domain.notifications.NotificationEntry
import com.example.roy.domain.notifications.NotificationSettings

internal data class NotificationViewState(
    val settings: NotificationSettings? = null,
    val items: List<NotificationEntry> = emptyList(),
    val nextCursor: String? = null,
    val isOnline: Boolean = false,
    val isSettingsLoading: Boolean = true,
    val isCenterLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val saving: NotificationChannel? = null,
    val settingsIssue: NotificationIssue? = null,
    val centerIssue: NotificationIssue? = null,
    val isPermissionBannerDismissed: Boolean = false,
)
