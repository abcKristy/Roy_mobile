package com.example.roy.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.auth.AuthMode
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Login light")
@Composable
internal fun AuthLoginPreview() {
    RoyTheme(darkTheme = false) {
        AuthView(AuthViewState(AuthMode.Login), emptyMap(), null, "") {}
    }
}

@Preview(name = "Registration dark")
@Composable
internal fun AuthRegistrationPreview() {
    RoyTheme(darkTheme = true) {
        AuthView(AuthViewState(AuthMode.Register), emptyMap(), null, "") {}
    }
}
