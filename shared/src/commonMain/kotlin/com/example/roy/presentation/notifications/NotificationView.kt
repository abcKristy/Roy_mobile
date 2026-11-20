package com.example.roy.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.roy.domain.notifications.NotificationChannel
import com.example.roy.domain.notifications.NotificationEntry
import com.example.roy.domain.notifications.PushPermission
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.core.formatLocalDateTime
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.notifications_back
import roy.shared.generated.resources.notifications_center
import roy.shared.generated.resources.notifications_conflict
import roy.shared.generated.resources.notifications_empty
import roy.shared.generated.resources.notifications_invalid_cursor
import roy.shared.generated.resources.notifications_loading
import roy.shared.generated.resources.notifications_more
import roy.shared.generated.resources.notifications_nearby_in_app
import roy.shared.generated.resources.notifications_nearby_push
import roy.shared.generated.resources.notifications_offline
import roy.shared.generated.resources.notifications_permission_allow
import roy.shared.generated.resources.notifications_permission_denied
import roy.shared.generated.resources.notifications_permission_later
import roy.shared.generated.resources.notifications_permission_manage
import roy.shared.generated.resources.notifications_permission_not_asked
import roy.shared.generated.resources.notifications_permission_settings
import roy.shared.generated.resources.notifications_personal
import roy.shared.generated.resources.notifications_reminders
import roy.shared.generated.resources.notifications_saving
import roy.shared.generated.resources.notifications_settings
import roy.shared.generated.resources.notifications_target_unavailable
import roy.shared.generated.resources.notifications_unavailable
import roy.shared.generated.resources.notifications_unread

@Composable
internal fun NotificationView(
    mode: NotificationMode,
    viewState: NotificationViewState,
    permission: PushPermission,
    eventHandler: (NotificationEvent) -> Unit,
) {
    val colors = RoyTheme.colors
    val spacing = RoyTheme.dimensions.spacing
    val refresh = { eventHandler(NotificationEvent.OnRefresh(mode)) }
    val loading = if (mode == NotificationMode.Settings) viewState.isSettingsLoading else viewState.isCenterLoading
    val issue = if (mode == NotificationMode.Settings) viewState.settingsIssue else viewState.centerIssue
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.comfortable),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        item {
            TertiaryButton(stringResource(Res.string.notifications_back), { eventHandler(NotificationEvent.OnBack) })
            BasicText(
                stringResource(if (mode == NotificationMode.Settings) Res.string.notifications_settings else Res.string.notifications_center),
                Modifier.semantics { heading() },
                style = RoyTheme.typography.headingMedium.copy(color = colors.textPrimary),
            )
        }
        if (mode == NotificationMode.Settings) {
            if (permission != PushPermission.Allowed) {
                item {
                    if (viewState.isPermissionBannerDismissed) {
                        TertiaryButton(stringResource(Res.string.notifications_permission_manage), {
                            eventHandler(NotificationEvent.OnDismissPermission(false))
                        })
                    } else {
                        PermissionBanner(
                            permission,
                            onRequest = { eventHandler(NotificationEvent.OnRequestPermission) },
                            onDismiss = { eventHandler(NotificationEvent.OnDismissPermission(true)) },
                        )
                    }
                }
            }
            viewState.settings?.let { settings ->
                item {
                    RoyCard(Modifier.fillMaxWidth()) {
                        val enabled = viewState.isOnline && !viewState.isSettingsLoading && viewState.saving == null
                        NotificationToggle(Res.string.notifications_reminders, settings.remindersPushEnabled, enabled) {
                            eventHandler(NotificationEvent.OnChange(NotificationChannel.RemindersPush, it))
                        }
                        NotificationToggle(Res.string.notifications_personal, settings.personalPushEnabled, enabled) {
                            eventHandler(NotificationEvent.OnChange(NotificationChannel.PersonalPush, it))
                        }
                        NotificationToggle(Res.string.notifications_nearby_push, settings.extraPushEnabled, enabled) {
                            eventHandler(NotificationEvent.OnChange(NotificationChannel.NearbyPush, it))
                        }
                        NotificationToggle(Res.string.notifications_nearby_in_app, settings.extraInAppEnabled, enabled) {
                            eventHandler(NotificationEvent.OnChange(NotificationChannel.NearbyInApp, it))
                        }
                    }
                }
            }
            if (viewState.saving != null) item { LoadingState(LoadingMode.Inline, stringResource(Res.string.notifications_saving)) }
        } else {
            items(viewState.items, key = NotificationEntry::id) { entry ->
                NotificationCard(entry, viewState.isOnline) { eventHandler(NotificationEvent.OnOpen(entry.id)) }
            }
        }
        if (loading || viewState.isLoadingMore) {
            item { LoadingState(LoadingMode.Inline, stringResource(Res.string.notifications_loading)) }
        } else if (issue != null) {
            item { ErrorState(issue.message(), viewState.isOnline, refresh) }
        } else if (mode == NotificationMode.Center && viewState.items.isEmpty()) {
            item {
                BasicText(
                    stringResource(Res.string.notifications_empty),
                    style = RoyTheme.typography.body.copy(color = colors.textSecondary),
                )
            }
        }
        if (mode == NotificationMode.Center && viewState.nextCursor != null && !loading && issue == null) {
            item {
                SecondaryButton(
                    stringResource(Res.string.notifications_more),
                    { eventHandler(NotificationEvent.OnMore) },
                    Modifier.fillMaxWidth(),
                    viewState.isOnline && !viewState.isLoadingMore,
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(permission: PushPermission, onRequest: () -> Unit, onDismiss: () -> Unit) {
    val denied = permission == PushPermission.Denied
    RoyCard(Modifier.fillMaxWidth()) {
        BasicText(
            stringResource(if (denied) Res.string.notifications_permission_denied else Res.string.notifications_permission_not_asked),
            style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textPrimary),
        )
        SecondaryButton(
            stringResource(if (denied) Res.string.notifications_permission_settings else Res.string.notifications_permission_allow),
            onRequest,
        )
        TertiaryButton(stringResource(Res.string.notifications_permission_later), onDismiss)
    }
}

@Composable
private fun NotificationToggle(label: StringResource, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = RoyTheme.colors
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = RoyTheme.dimensions.minimumTouchTarget)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            stringResource(label), Modifier.weight(1f),
            style = RoyTheme.typography.body.copy(color = if (enabled) colors.textPrimary else colors.textDisabled),
        )
        Box(
            Modifier.size(width = 42.dp, height = 24.dp)
                .background(if (checked && enabled) colors.accent else colors.secondary, CircleShape)
                .border(1.dp, colors.outline, CircleShape).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(18.dp).background(if (enabled) colors.textPrimary else colors.textDisabled, CircleShape))
        }
    }
}

@Composable
private fun NotificationCard(entry: NotificationEntry, enabled: Boolean, onOpen: () -> Unit) {
    val colors = RoyTheme.colors
    RoyCard(Modifier.fillMaxWidth(), onClick = onOpen.takeIf { enabled && (entry.target != null || entry.readAt == null) }) {
        if (entry.readAt == null) {
            BasicText(
                stringResource(Res.string.notifications_unread),
                style = RoyTheme.typography.bodySmall.copy(color = colors.accent),
            )
        }
        BasicText(entry.title, style = RoyTheme.typography.headingSmall.copy(color = colors.textPrimary))
        if (entry.body.isNotBlank()) {
            BasicText(entry.body, style = RoyTheme.typography.body.copy(color = colors.textPrimary))
        }
        BasicText(formatLocalDateTime(entry.createdAt), style = RoyTheme.typography.bodySmall.copy(color = colors.textSecondary))
    }
}

@Composable
private fun NotificationIssue.message(): String = stringResource(when (this) {
    NotificationIssue.Offline -> Res.string.notifications_offline
    NotificationIssue.Conflict -> Res.string.notifications_conflict
    NotificationIssue.InvalidCursor -> Res.string.notifications_invalid_cursor
    NotificationIssue.Unavailable -> Res.string.notifications_unavailable
    NotificationIssue.TargetUnavailable -> Res.string.notifications_target_unavailable
})
