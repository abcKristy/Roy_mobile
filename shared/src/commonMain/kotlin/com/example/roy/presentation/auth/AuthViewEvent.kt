package com.example.roy.presentation.auth


internal sealed class AuthViewEvent {
    data class OnPhoneChanged(val value: String) : AuthViewEvent()
    data class OnDisplayNameChanged(val value: String) : AuthViewEvent()
    data class OnAgeChanged(val value: String) : AuthViewEvent()
    data class OnConsentChanged(val checked: Boolean) : AuthViewEvent()
    class OnSubmit(val password: String) : AuthViewEvent()
    class OnPasswordChanged(val value: String) : AuthViewEvent()
    data object OnTogglePasswordVisibility : AuthViewEvent()
    data object OnOpenPolicy : AuthViewEvent()
    data object OnRetryPolicy : AuthViewEvent()
    data object OnSwitchMode : AuthViewEvent()
}
