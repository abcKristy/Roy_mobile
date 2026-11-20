package com.example.roy.presentation.auth


internal sealed class AuthViewAction {
    data class OpenPolicy(val url: String) : AuthViewAction()
    data object SwitchMode : AuthViewAction()
    data object Succeeded : AuthViewAction()
}
