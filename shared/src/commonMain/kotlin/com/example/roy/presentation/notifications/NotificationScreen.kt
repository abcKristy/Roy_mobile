package com.example.roy.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.notifications.NotificationChannel
import com.example.roy.domain.notifications.NotificationEntry
import com.example.roy.domain.notifications.NotificationTarget
import com.example.roy.domain.notifications.PushPermission
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.core.formatLocalDateTime
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NotificationScreen(
    viewModel: NotificationViewModel,
    mode: NotificationMode,
    permission: PushPermission,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    onOpenTarget: (NotificationTarget) -> Unit,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(viewModel) { viewModel::handleEvent }
    LifecycleResumeEffect(viewModel, mode) {
        if (mode == NotificationMode.Center) viewModel.setCenterVisible(true)
        eventHandler(NotificationEvent.Refresh(mode))
        onPauseOrDispose { if (mode == NotificationMode.Center) viewModel.setCenterVisible(false) }
    }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            NotificationAction.Back -> onBack()
            NotificationAction.RequestPermission -> onRequestPermission()
            is NotificationAction.Open -> onOpenTarget(action.target)
        }
    }
    BackHandler { eventHandler(NotificationEvent.Back) }
    NotificationView(mode, state, permission, eventHandler)
}

@Composable
internal fun NotificationView(
    mode: NotificationMode,
    state: NotificationViewState,
    permission: PushPermission,
    eventHandler: (NotificationEvent) -> Unit,
) {
    val colors = RoyTheme.colors
    val spacing = RoyTheme.dimensions.spacing
    val refresh = { eventHandler(NotificationEvent.Refresh(mode)) }
    val loading = if (mode == NotificationMode.Settings) state.settingsLoading else state.centerLoading
    val issue = if (mode == NotificationMode.Settings) state.settingsIssue else state.centerIssue
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.comfortable),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        item {
            TertiaryButton(stringResource(Res.string.notifications_back), { eventHandler(NotificationEvent.Back) })
            BasicText(
                stringResource(if (mode == NotificationMode.Settings) Res.string.notifications_settings else Res.string.notifications_center),
                Modifier.semantics { heading() },
                style = RoyTheme.typography.headingMedium.copy(color = colors.textPrimary),
            )
        }
        if (mode == NotificationMode.Settings) {
            if (permission != PushPermission.Allowed) {
                item {
                    if (state.permissionBannerDismissed) {
                        TertiaryButton(stringResource(Res.string.notifications_permission_manage), {
                            eventHandler(NotificationEvent.DismissPermission(false))
                        })
                    } else {
                        PermissionBanner(
                            permission,
                            onRequest = { eventHandler(NotificationEvent.RequestPermission) },
                            onDismiss = { eventHandler(NotificationEvent.DismissPermission(true)) },
                        )
                    }
                }
            }
            state.settings?.let { settings ->
                item {
                    RoyCard(Modifier.fillMaxWidth()) {
                        val enabled = state.online && !state.settingsLoading && state.saving == null
                        NotificationToggle(Res.string.notifications_reminders, settings.remindersPushEnabled, enabled) {
                            eventHandler(NotificationEvent.Change(NotificationChannel.RemindersPush, it))
                        }
                        NotificationToggle(Res.string.notifications_personal, settings.personalPushEnabled, enabled) {
                            eventHandler(NotificationEvent.Change(NotificationChannel.PersonalPush, it))
                        }
                        NotificationToggle(Res.string.notifications_nearby_push, settings.extraPushEnabled, enabled) {
                            eventHandler(NotificationEvent.Change(NotificationChannel.NearbyPush, it))
                        }
                        NotificationToggle(Res.string.notifications_nearby_in_app, settings.extraInAppEnabled, enabled) {
                            eventHandler(NotificationEvent.Change(NotificationChannel.NearbyInApp, it))
                        }
                    }
                }
            }
            if (state.saving != null) item { LoadingState(LoadingMode.Inline, stringResource(Res.string.notifications_saving)) }
        } else {
            items(state.items, key = NotificationEntry::id) { entry ->
                NotificationCard(entry, state.online) { eventHandler(NotificationEvent.Open(entry.id)) }
            }
        }
        if (loading || state.loadingMore) {
            item { LoadingState(LoadingMode.Inline, stringResource(Res.string.notifications_loading)) }
        } else if (issue != null) {
            item { ErrorState(issue.message(), state.online, refresh) }
        } else if (mode == NotificationMode.Center && state.items.isEmpty()) {
            item {
                BasicText(
                    stringResource(Res.string.notifications_empty),
                    style = RoyTheme.typography.body.copy(color = colors.textSecondary),
                )
            }
        }
        if (mode == NotificationMode.Center && state.nextCursor != null && !loading && issue == null) {
            item {
                SecondaryButton(
                    stringResource(Res.string.notifications_more),
                    { eventHandler(NotificationEvent.More) },
                    Modifier.fillMaxWidth(),
                    state.online && !state.loadingMore,
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
