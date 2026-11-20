package com.example.roy.presentation.auth

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthField
import com.example.roy.domain.auth.AuthFormInput
import com.example.roy.domain.auth.AuthMode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthValidationResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.ValidateAuthInput
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.launch

internal class AuthViewModel(
    mode: AuthMode,
    private val session: SessionCoordinator,
    private val validate: ValidateAuthInput,
) : BaseViewModel<AuthViewState, AuthViewEvent, AuthViewAction>(AuthViewState(mode)) {
    init {
        if (mode == AuthMode.Register) loadPolicy()
    }

    override fun handleEvent(event: AuthViewEvent) {
        if (viewState.value.isSubmitting) return
        when (event) {
            is AuthViewEvent.OnPhoneChanged -> onPhoneChanged(event)
            is AuthViewEvent.OnDisplayNameChanged -> onDisplayNameChanged(event)
            is AuthViewEvent.OnAgeChanged -> onAgeChanged(event)
            is AuthViewEvent.OnConsentChanged -> onConsentChanged(event)
            is AuthViewEvent.OnSubmit -> onSubmit(event.password)
            is AuthViewEvent.OnPasswordChanged -> onPasswordChanged()
            AuthViewEvent.OnTogglePasswordVisibility -> onTogglePasswordVisibility()
            AuthViewEvent.OnOpenPolicy -> onOpenPolicy()
            AuthViewEvent.OnRetryPolicy -> onRetryPolicy()
            AuthViewEvent.OnSwitchMode -> onSwitchMode()
        }
    }

    private fun onPhoneChanged(event: AuthViewEvent.OnPhoneChanged) {
        edit(AuthField.Phone) { copy(phone = event.value) }
    }

    private fun onDisplayNameChanged(event: AuthViewEvent.OnDisplayNameChanged) {
        edit(AuthField.DisplayName) { copy(displayName = event.value) }
    }

    private fun onAgeChanged(event: AuthViewEvent.OnAgeChanged) {
        edit(AuthField.AgeYears) { copy(ageInput = event.value) }
    }

    private fun onConsentChanged(event: AuthViewEvent.OnConsentChanged) {
        edit(AuthField.Consent) {
            copy(acceptedPrivacyPolicyVersion = policy?.version?.takeIf { event.checked })
        }
    }

    private fun onPasswordChanged() {
        edit(AuthField.Password) { this }
    }

    private fun onTogglePasswordVisibility() {
        updateState { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    private fun onOpenPolicy() {
        viewState.value.policy?.url?.let { url ->
            viewModelScope.launch { sendAction(AuthViewAction.OpenPolicy(url)) }
        }
    }

    private fun onRetryPolicy() {
        loadPolicy()
    }

    private fun onSwitchMode() {
        updateState { it.copy(isPasswordVisible = false) }
        viewModelScope.launch { sendAction(AuthViewAction.SwitchMode) }
    }

    private fun edit(field: AuthField, change: AuthViewState.() -> AuthViewState) {
        updateState {
            it.change().copy(
                fieldErrors = it.fieldErrors - field,
                remoteFieldError = it.remoteFieldError?.takeUnless { previous -> previous == field },
                formError = null,
            )
        }
    }

    private fun onSubmit(password: String) {
        val state = viewState.value
        if (state.mode == AuthMode.Register && (state.policy == null || state.isPolicyLoading)) return
        val result = validate(
            AuthFormInput(
                mode = state.mode,
                phone = state.phone,
                password = password,
                displayName = state.displayName,
                ageInput = state.ageInput,
                acceptedPrivacyPolicyVersion = state.acceptedPrivacyPolicyVersion,
            ),
        )
        when (result) {
            is AuthValidationResult.Invalid -> updateState {
                it.copy(fieldErrors = result.errors, remoteFieldError = null, formError = null)
            }
            is AuthValidationResult.Valid -> {
                updateState {
                    it.copy(isSubmitting = true, isPasswordVisible = false, fieldErrors = emptyMap(),
                        remoteFieldError = null, formError = null)
                }
                viewModelScope.launch {
                    when (val auth = session.authenticate(result.credentials)) {
                        is AuthResult.Success -> {
                            updateState { it.copy(isSubmitting = false) }
                            sendAction(AuthViewAction.Succeeded)
                        }
                        is AuthResult.Failure -> {
                            val error = auth.error
                            val field = when (error.code) {
                                AuthErrorCode.DuplicatePhone -> AuthField.Phone
                                AuthErrorCode.PolicyAcceptanceRequired -> null
                                else -> error.field
                            }
                            updateState {
                                it.copy(isSubmitting = false, remoteFieldError = field, formError = error.code)
                            }
                            if (error.code == AuthErrorCode.PolicyAcceptanceRequired) loadPolicy(invalidateConsent = true)
                        }
                    }
                }
            }
        }
    }

    private fun loadPolicy(invalidateConsent: Boolean = false) {
        val state = viewState.value
        if (state.mode != AuthMode.Register || state.isPolicyLoading) return
        updateState {
            it.copy(
                isPolicyLoading = true,
                acceptedPrivacyPolicyVersion = it.acceptedPrivacyPolicyVersion.takeUnless { invalidateConsent },
            )
        }
        viewModelScope.launch {
            val result = session.policy()
            val current = (result as? AuthResult.Success)?.value
            updateState {
                it.copy(
                    isPolicyLoading = false,
                    policy = current,
                    acceptedPrivacyPolicyVersion = it.acceptedPrivacyPolicyVersion?.takeIf { version ->
                        version == current?.version
                    },
                )
            }
        }
    }
}
