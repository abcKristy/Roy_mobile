package com.example.roy.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthField
import com.example.roy.domain.auth.AuthFieldError
import com.example.roy.domain.auth.AuthMode
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.ValidateAuthInput
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.core.CollectMviActions
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_consent
import roy.shared.generated.resources.auth_duplicate_phone
import roy.shared.generated.resources.auth_invalid_age
import roy.shared.generated.resources.auth_invalid_credentials
import roy.shared.generated.resources.auth_invalid_field
import roy.shared.generated.resources.auth_invalid_phone
import roy.shared.generated.resources.auth_name_length
import roy.shared.generated.resources.auth_network
import roy.shared.generated.resources.auth_password_length
import roy.shared.generated.resources.auth_password_required
import roy.shared.generated.resources.auth_policy_changed
import roy.shared.generated.resources.auth_policy_open_error
import roy.shared.generated.resources.auth_rate_limited
import roy.shared.generated.resources.auth_storage_failed
import roy.shared.generated.resources.auth_unavailable
import roy.shared.generated.resources.protected_cancel

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun AuthScreen(
    mode: AuthMode,
    session: SessionCoordinator,
    onCancel: (() -> Unit)?,
    onSucceeded: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val model = viewModel { AuthViewModel(mode, session, ValidateAuthInput()) }
    val viewState by model.viewState.collectAsStateWithLifecycle()
    if (onCancel != null) BackHandler(enabled = !viewState.isSubmitting, onBack = onCancel)
    SuppressBackWhileSubmitting(viewState.isSubmitting)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var modeSwitchRequested by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { modeSwitchRequested = false }
    var password by remember { mutableStateOf("") }
    var policyOpenFailed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val eventHandler: (AuthViewEvent) -> Unit = handleEvent@{ event ->
        if (event == AuthViewEvent.OnSwitchMode) {
            if (modeSwitchRequested || model.viewState.value.isSubmitting ||
                lifecycle.currentState != Lifecycle.State.RESUMED
            ) return@handleEvent
            // The outgoing destination can still receive taps while its navigation action is queued.
            modeSwitchRequested = true
        }
        if (event is AuthViewEvent.OnPasswordChanged && !model.viewState.value.isSubmitting) password = event.value
        if (event is AuthViewEvent.OnSubmit || event == AuthViewEvent.OnRetryPolicy) policyOpenFailed = false
        model.handleEvent(event)
        if (event is AuthViewEvent.OnSubmit && model.viewState.value.isSubmitting) password = ""
    }

    CollectMviActions(model.viewAction) { action ->
        when (action) {
            AuthViewAction.SwitchMode -> {
                password = ""
                onSwitchMode()
            }
            AuthViewAction.Succeeded -> onSucceeded()
            is AuthViewAction.OpenPolicy -> {
                policyOpenFailed = try {
                    uriHandler.openUri(action.url)
                    false
                } catch (_: Exception) {
                    true
                }
            }
        }
    }

    val errors = mutableMapOf<AuthField, String>()
    for ((field, error) in viewState.fieldErrors) {
        errors[field] = stringResource(error.resource())
    }
    val remoteField = viewState.remoteFieldError
    if (remoteField != null) {
        val resource = if (viewState.formError == AuthErrorCode.DuplicatePhone) {
            Res.string.auth_duplicate_phone
        } else {
            Res.string.auth_invalid_field
        }
        errors[remoteField] = stringResource(resource)
    }
    val formError = when {
        policyOpenFailed -> stringResource(Res.string.auth_policy_open_error)
        viewState.remoteFieldError != null -> null
        else -> viewState.formError?.let { stringResource(it.resource()) }
    }
    if (onCancel == null) {
        AuthView(viewState, errors, formError, password, eventHandler)
    } else {
        Column(Modifier.fillMaxSize()) {
            TertiaryButton(
                label = stringResource(Res.string.protected_cancel),
                onClick = onCancel,
                isEnabled = !viewState.isSubmitting,
            )
            AuthView(viewState, errors, formError, password, eventHandler)
        }
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SuppressBackWhileSubmitting(isSubmitting: Boolean) {
    BackHandler(enabled = isSubmitting) { /* Keep ownership of the request until persistence finishes. */ }
}

private fun AuthFieldError.resource(): StringResource = when (this) {
    AuthFieldError.InvalidPhone -> Res.string.auth_invalid_phone
    AuthFieldError.PasswordRequired -> Res.string.auth_password_required
    AuthFieldError.PasswordLength -> Res.string.auth_password_length
    AuthFieldError.DisplayNameLength -> Res.string.auth_name_length
    AuthFieldError.InvalidAge -> Res.string.auth_invalid_age
    AuthFieldError.ConsentRequired -> Res.string.auth_consent
}

private fun AuthErrorCode.resource(): StringResource = when (this) {
    AuthErrorCode.InvalidCredentials -> Res.string.auth_invalid_credentials
    AuthErrorCode.Network -> Res.string.auth_network
    AuthErrorCode.PolicyAcceptanceRequired -> Res.string.auth_policy_changed
    AuthErrorCode.RateLimited -> Res.string.auth_rate_limited
    AuthErrorCode.Storage -> Res.string.auth_storage_failed
    else -> Res.string.auth_unavailable
}
