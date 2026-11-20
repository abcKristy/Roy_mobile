package com.example.roy.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.example.roy.domain.auth.AuthField
import com.example.roy.domain.auth.AuthMode
import com.example.roy.presentation.auth.components.AgeField
import com.example.roy.presentation.auth.components.AuthModeSwitch
import com.example.roy.presentation.auth.components.AuthSubmitButton
import com.example.roy.presentation.auth.components.ConsentCheckbox
import com.example.roy.presentation.auth.components.DisplayNameField
import com.example.roy.presentation.auth.components.PasswordField
import com.example.roy.presentation.auth.components.PasswordVisibilityButton
import com.example.roy.presentation.auth.components.PhoneField
import com.example.roy.presentation.auth.components.PolicyLink
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_age
import roy.shared.generated.resources.auth_back
import roy.shared.generated.resources.auth_consent
import roy.shared.generated.resources.auth_create_intro
import roy.shared.generated.resources.auth_create_title
import roy.shared.generated.resources.auth_create_title_button
import roy.shared.generated.resources.auth_hide_password
import roy.shared.generated.resources.auth_login_button
import roy.shared.generated.resources.auth_login_intro
import roy.shared.generated.resources.auth_login_title
import roy.shared.generated.resources.auth_name
import roy.shared.generated.resources.auth_password
import roy.shared.generated.resources.auth_phone
import roy.shared.generated.resources.auth_policy
import roy.shared.generated.resources.auth_policy_loading
import roy.shared.generated.resources.auth_policy_retry
import roy.shared.generated.resources.auth_return_login
import roy.shared.generated.resources.auth_show_password
import roy.shared.generated.resources.auth_switch_register

@Composable
internal fun AuthView(
    viewState: AuthViewState,
    errors: Map<AuthField, String>,
    formError: String?,
    password: String,
    eventHandler: (AuthViewEvent) -> Unit,
) {
    val isRegistration = viewState.mode == AuthMode.Register
    val enabled = !viewState.isSubmitting
    val ready = enabled && (!isRegistration ||
        (viewState.policy != null && !viewState.isPolicyLoading &&
            viewState.acceptedPrivacyPolicyVersion == viewState.policy.version))
    val spacing = RoyTheme.dimensions.spacing
    var phoneFocusRequest by remember { mutableIntStateOf(0) }
    val passwordFocus = remember { FocusRequester() }
    val ageFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val submit = {
        if (ready) {
            focus.clearFocus()
            eventHandler(AuthViewEvent.OnSubmit(password))
        }
    }

    Column(
        Modifier.fillMaxSize().background(RoyTheme.colors.background)
            .safeContentPadding().imePadding().padding(horizontal = spacing.comfortable)
            .padding(top = spacing.comfortable, bottom = spacing.medium),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.compact),
        ) {
            if (isRegistration) {
                AuthBackButton(enabled) { eventHandler(AuthViewEvent.OnSwitchMode) }
            }
            BasicText(
                stringResource(if (isRegistration) Res.string.auth_create_title else Res.string.auth_login_title),
                Modifier.semantics { heading() },
                style = RoyTheme.typography.headingLarge.copy(color = RoyTheme.colors.textPrimary),
            )
            BasicText(
                stringResource(if (isRegistration) Res.string.auth_create_intro else Res.string.auth_login_intro),
                style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.textSecondary),
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                if (isRegistration) {
                    DisplayNameField(
                        value = viewState.displayName,
                        label = stringResource(Res.string.auth_name),
                        errorText = errors[AuthField.DisplayName],
                        isEnabled = enabled,
                        onValueChange = { eventHandler(AuthViewEvent.OnDisplayNameChanged(it)) },
                        onImeNext = { phoneFocusRequest++ },
                    )
                }
                val phoneLabel = stringResource(Res.string.auth_phone)
                PhoneField(
                    value = viewState.phone,
                    label = phoneLabel,
                    contentDescription = phoneLabel,
                    errorText = errors[AuthField.Phone],
                    isEnabled = enabled,
                    onValueChange = { eventHandler(AuthViewEvent.OnPhoneChanged(it)) },
                    onImeNext = { passwordFocus.requestFocus() },
                    focusRequest = phoneFocusRequest,
                )
                PasswordField(
                    value = password,
                    label = stringResource(Res.string.auth_password),
                    errorText = errors[AuthField.Password],
                    isEnabled = enabled,
                    imeAction = if (isRegistration) ImeAction.Next else ImeAction.Done,
                    onValueChange = { eventHandler(AuthViewEvent.OnPasswordChanged(it)) },
                    onImeAction = if (isRegistration) ({ ageFocus.requestFocus(); Unit }) else submit,
                    modifier = Modifier.focusRequester(passwordFocus),
                    isVisible = viewState.isPasswordVisible,
                    visibilityButton = {
                        PasswordVisibilityButton(
                            isVisible = viewState.isPasswordVisible,
                            isEnabled = enabled,
                            showLabel = stringResource(Res.string.auth_show_password),
                            hideLabel = stringResource(Res.string.auth_hide_password),
                            onToggle = { eventHandler(AuthViewEvent.OnTogglePasswordVisibility) },
                        )
                    },
                )
                if (isRegistration) {
                    AgeField(
                        ageText = viewState.ageInput,
                        label = stringResource(Res.string.auth_age),
                        errorText = errors[AuthField.AgeYears],
                        isEnabled = enabled,
                        imeAction = ImeAction.Done,
                        onValueChange = { eventHandler(AuthViewEvent.OnAgeChanged(it)) },
                        onImeAction = submit,
                        modifier = Modifier.focusRequester(ageFocus),
                    )
                }
            }
            if (!isRegistration) {
                AuthModeSwitch(stringResource(Res.string.auth_switch_register), enabled, {
                    eventHandler(AuthViewEvent.OnSwitchMode)
                })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConsentCheckbox(
                        isChecked = viewState.acceptedPrivacyPolicyVersion != null,
                        isEnabled = enabled && !viewState.isPolicyLoading,
                        policyVersion = viewState.policy?.version,
                        label = stringResource(Res.string.auth_consent),
                        onCheckedChange = { eventHandler(AuthViewEvent.OnConsentChanged(it)) },
                    )
                    PolicyLink(
                        url = viewState.policy?.url,
                        isLoading = viewState.isPolicyLoading,
                        isEnabled = enabled,
                        policyLabel = stringResource(Res.string.auth_policy),
                        loadingLabel = stringResource(Res.string.auth_policy_loading),
                        retryLabel = stringResource(Res.string.auth_policy_retry),
                        onOpen = { eventHandler(AuthViewEvent.OnOpenPolicy) },
                        onRetry = { eventHandler(AuthViewEvent.OnRetryPolicy) },
                    )
                }
            }
            if (formError != null) {
                BasicText(
                    formError,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.error),
                )
            }
        }
        AuthSubmitButton(
            text = stringResource(if (isRegistration) Res.string.auth_create_title_button else Res.string.auth_login_button),
            isEnabled = ready,
            isSubmitting = viewState.isSubmitting,
            onSubmit = submit,
        )
    }
}

@Composable
private fun AuthBackButton(enabled: Boolean, onClick: () -> Unit) {
    val dimensions = RoyTheme.dimensions
    Box(
        Modifier.size(dimensions.minimumTouchTarget).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(Res.drawable.auth_back),
            contentDescription = stringResource(Res.string.auth_return_login),
            modifier = Modifier.size(dimensions.iconSize),
            colorFilter = ColorFilter.tint(RoyTheme.colors.textPrimary),
        )
    }
}
