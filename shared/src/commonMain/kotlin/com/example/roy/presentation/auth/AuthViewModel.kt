package com.example.roy.presentation.auth

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthField
import com.example.roy.domain.auth.AuthFieldError
import com.example.roy.domain.auth.AuthFormInput
import com.example.roy.domain.auth.AuthMode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthValidationResult
import com.example.roy.domain.auth.PrivacyPolicy
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.ValidateAuthInput
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.launch

internal data class AuthViewState(
    val mode: AuthMode,
    val phone: String = "",
    val displayName: String = "",
    val ageInput: String = "",
    val policy: PrivacyPolicy? = null,
    val policyLoading: Boolean = false,
    val acceptedPrivacyPolicyVersion: String? = null,
    val passwordVisible: Boolean = false,
    val submitting: Boolean = false,
    val fieldErrors: Map<AuthField, AuthFieldError> = emptyMap(),
    val remoteFieldError: AuthField? = null,
    val formError: AuthErrorCode? = null,
)

internal sealed interface AuthViewEvent {
    data class PhoneChanged(val value: String) : AuthViewEvent
    data class DisplayNameChanged(val value: String) : AuthViewEvent
    data class AgeChanged(val value: String) : AuthViewEvent
    data class ConsentChanged(val checked: Boolean) : AuthViewEvent
    class Submit(val password: String) : AuthViewEvent
    class PasswordChanged(val value: String) : AuthViewEvent
    data object TogglePasswordVisibility : AuthViewEvent
    data object OpenPolicy : AuthViewEvent
    data object RetryPolicy : AuthViewEvent
    data object SwitchMode : AuthViewEvent
}

internal sealed interface AuthViewAction {
    data class OpenPolicy(val url: String) : AuthViewAction
    data object SwitchMode : AuthViewAction
}

internal class AuthViewModel(
    mode: AuthMode,
    private val session: SessionCoordinator,
    private val validate: ValidateAuthInput,
) : BaseViewModel<AuthViewState, AuthViewEvent, AuthViewAction>(AuthViewState(mode)) {
    init {
        if (mode == AuthMode.Register) loadPolicy()
    }

    override fun handleEvent(event: AuthViewEvent) {
        if (viewState.value.submitting) return
        when (event) {
            is AuthViewEvent.PhoneChanged -> edit(AuthField.Phone) { copy(phone = event.value) }
            is AuthViewEvent.DisplayNameChanged -> edit(AuthField.DisplayName) { copy(displayName = event.value) }
            is AuthViewEvent.AgeChanged -> edit(AuthField.AgeYears) { copy(ageInput = event.value) }
            is AuthViewEvent.ConsentChanged -> edit(AuthField.Consent) {
                copy(acceptedPrivacyPolicyVersion = policy?.version?.takeIf { event.checked })
            }
            is AuthViewEvent.Submit -> submit(event.password)
            is AuthViewEvent.PasswordChanged -> edit(AuthField.Password) { this }
            AuthViewEvent.TogglePasswordVisibility -> updateState { it.copy(passwordVisible = !it.passwordVisible) }
            AuthViewEvent.OpenPolicy -> viewState.value.policy?.url?.let { url ->
                viewModelScope.launch { sendAction(AuthViewAction.OpenPolicy(url)) }
            }
            AuthViewEvent.RetryPolicy -> loadPolicy()
            AuthViewEvent.SwitchMode -> {
                updateState { it.copy(passwordVisible = false) }
                viewModelScope.launch { sendAction(AuthViewAction.SwitchMode) }
            }
        }
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

    private fun submit(password: String) {
        val state = viewState.value
        if (state.mode == AuthMode.Register && (state.policy == null || state.policyLoading)) return
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
                    it.copy(submitting = true, passwordVisible = false, fieldErrors = emptyMap(),
                        remoteFieldError = null, formError = null)
                }
                viewModelScope.launch {
                    when (val auth = session.authenticate(result.credentials)) {
                        is AuthResult.Success -> updateState { it.copy(submitting = false) }
                        is AuthResult.Failure -> {
                            val error = auth.error
                            val field = when (error.code) {
                                AuthErrorCode.DuplicatePhone -> AuthField.Phone
                                AuthErrorCode.PolicyAcceptanceRequired -> null
                                else -> error.field
                            }
                            updateState {
                                it.copy(submitting = false, remoteFieldError = field, formError = error.code)
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
        if (state.mode != AuthMode.Register || state.policyLoading) return
        updateState {
            it.copy(
                policyLoading = true,
                acceptedPrivacyPolicyVersion = it.acceptedPrivacyPolicyVersion.takeUnless { invalidateConsent },
            )
        }
        viewModelScope.launch {
            val result = session.policy()
            val current = (result as? AuthResult.Success)?.value
            updateState {
                it.copy(
                    policyLoading = false,
                    policy = current,
                    acceptedPrivacyPolicyVersion = it.acceptedPrivacyPolicyVersion?.takeIf { version ->
                        version == current?.version
                    },
                )
            }
        }
    }
}
