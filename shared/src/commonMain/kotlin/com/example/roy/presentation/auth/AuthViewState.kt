package com.example.roy.presentation.auth

import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthField
import com.example.roy.domain.auth.AuthFieldError
import com.example.roy.domain.auth.AuthMode
import com.example.roy.domain.auth.PrivacyPolicy

internal data class AuthViewState(
    val mode: AuthMode,
    val phone: String = "",
    val displayName: String = "",
    val ageInput: String = "",
    val policy: PrivacyPolicy? = null,
    val isPolicyLoading: Boolean = false,
    val acceptedPrivacyPolicyVersion: String? = null,
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val fieldErrors: Map<AuthField, AuthFieldError> = emptyMap(),
    val remoteFieldError: AuthField? = null,
    val formError: AuthErrorCode? = null,
)
