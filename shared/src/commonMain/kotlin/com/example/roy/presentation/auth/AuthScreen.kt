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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthField
import com.example.roy.domain.auth.AuthFieldError
import com.example.roy.domain.auth.AuthMode
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.ValidateAuthInput
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.navigation.RoyRoute
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.*

@Composable
internal fun AuthNavHost(
    session: SessionCoordinator,
    onCancel: (() -> Unit)? = null,
    onSucceeded: () -> Unit = {},
) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = RoyRoute.Login) {
        composable<RoyRoute.Login> {
            AuthScreen(AuthMode.Login, session, onCancel, onSucceeded) {
                if (nav.currentDestination?.hasRoute<RoyRoute.Login>() == true) {
                    nav.navigate(RoyRoute.Register) { launchSingleTop = true }
                }
            }
        }
        composable<RoyRoute.Register> {
            AuthScreen(AuthMode.Register, session, onCancel, onSucceeded) {
                if (nav.currentDestination?.hasRoute<RoyRoute.Register>() == true) {
                    nav.popBackStack<RoyRoute.Login>(inclusive = false)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AuthScreen(
    mode: AuthMode,
    session: SessionCoordinator,
    onCancel: (() -> Unit)?,
    onSucceeded: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val model = viewModel { AuthViewModel(mode, session, ValidateAuthInput()) }
    val state by model.viewState.collectAsStateWithLifecycle()
    if (onCancel != null) BackHandler(enabled = !state.submitting, onBack = onCancel)
    SuppressBackWhileSubmitting(state.submitting)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var modeSwitchRequested by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { modeSwitchRequested = false }
    var password by remember { mutableStateOf("") }
    var policyOpenFailed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val eventHandler: (AuthViewEvent) -> Unit = handleEvent@{ event ->
        if (event == AuthViewEvent.SwitchMode) {
            if (modeSwitchRequested || model.viewState.value.submitting ||
                lifecycle.currentState != Lifecycle.State.RESUMED
            ) return@handleEvent
            // The outgoing destination can still receive taps while its navigation action is queued.
            modeSwitchRequested = true
        }
        if (event is AuthViewEvent.PasswordChanged && !model.viewState.value.submitting) password = event.value
        if (event is AuthViewEvent.Submit || event == AuthViewEvent.RetryPolicy) policyOpenFailed = false
        model.handleEvent(event)
        if (event is AuthViewEvent.Submit && model.viewState.value.submitting) password = ""
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
    for ((field, error) in state.fieldErrors) {
        errors[field] = stringResource(error.resource())
    }
    val remoteField = state.remoteFieldError
    if (remoteField != null) {
        val resource = if (state.formError == AuthErrorCode.DuplicatePhone) {
            Res.string.auth_duplicate_phone
        } else {
            Res.string.auth_invalid_field
        }
        errors[remoteField] = stringResource(resource)
    }
    val formError = when {
        policyOpenFailed -> stringResource(Res.string.auth_policy_open_error)
        state.remoteFieldError != null -> null
        else -> state.formError?.let { stringResource(it.resource()) }
    }
    if (onCancel == null) {
        AuthView(state, errors, formError, password, eventHandler)
    } else {
        Column(Modifier.fillMaxSize()) {
            TertiaryButton(
                label = stringResource(Res.string.protected_cancel),
                onClick = onCancel,
                isEnabled = !state.submitting,
            )
            AuthView(state, errors, formError, password, eventHandler)
        }
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SuppressBackWhileSubmitting(submitting: Boolean) {
    BackHandler(enabled = submitting) { /* Keep ownership of the request until persistence finishes. */ }
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
