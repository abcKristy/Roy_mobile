package com.example.roy.presentation.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.notifications.NotificationTarget
import com.example.roy.domain.notifications.PushPermission
import com.example.roy.presentation.core.CollectMviActions

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
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(viewModel) { viewModel::handleEvent }
    LifecycleResumeEffect(viewModel, mode) {
        if (mode == NotificationMode.Center) viewModel.setCenterVisible(true)
        eventHandler(NotificationEvent.OnRefresh(mode))
        onPauseOrDispose { if (mode == NotificationMode.Center) viewModel.setCenterVisible(false) }
    }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            NotificationAction.Back -> onBack()
            NotificationAction.RequestPermission -> onRequestPermission()
            is NotificationAction.Open -> onOpenTarget(action.target)
        }
    }
    BackHandler { eventHandler(NotificationEvent.OnBack) }
    NotificationView(mode, viewState, permission, eventHandler)
}
