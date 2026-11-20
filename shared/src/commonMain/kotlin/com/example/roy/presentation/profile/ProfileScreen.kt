package com.example.roy.presentation.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.presentation.core.CollectMviActions
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_policy_open_error

@Composable
internal fun ProfileScreen(
    model: ProfileViewModel,
    onOpenNotifications: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
) {
    val viewState by model.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(model) { model::handleEvent }
    val uri = LocalUriHandler.current
    var policyOpenFailed by remember(model) { mutableStateOf(false) }
    val pickImage = rememberProfileImagePicker { eventHandler(ProfileViewEvent.OnAvatarPicked(it)) }
    LaunchedEffect(model) { eventHandler(ProfileViewEvent.OnLoad) }
    DisposableEffect(model) { onDispose { eventHandler(ProfileViewEvent.OnScreenClosed) } }
    CollectMviActions(model.viewAction) { action ->
        when (action) {
            ProfileViewAction.OpenImagePicker -> pickImage()
            ProfileViewAction.OpenNotifications -> onOpenNotifications()
            ProfileViewAction.OpenNotificationSettings -> onOpenNotificationSettings()
            is ProfileViewAction.OpenPublishedPolicy -> {
                policyOpenFailed = try { uri.openUri(action.url); false }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { true }
            }
        }
    }
    ProfileView(viewState, eventHandler, if (policyOpenFailed) stringResource(Res.string.auth_policy_open_error) else null)
    viewState.dialog?.let { ProfileConfirmation(viewState, it, eventHandler) }
}
