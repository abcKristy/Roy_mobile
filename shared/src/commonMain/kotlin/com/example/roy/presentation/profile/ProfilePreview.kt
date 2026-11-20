package com.example.roy.presentation.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.auth.AuthUser
import com.example.roy.presentation.theme.RoyTheme
import kotlin.time.Instant

private val previewUser = AuthUser("preview", "Анна Тихонова", null, "+447700900200", 25, null, Instant.DISTANT_PAST)

@Preview(name = "Profile light")
@Composable
private fun ProfileLightPreview() {
    RoyTheme(darkTheme = false) {
        ProfileView(ProfileViewState(user = previewUser, nameDraft = previewUser.displayName), {})
    }
}

@Preview(name = "Profile dark")
@Composable
private fun ProfileDarkPreview() {
    RoyTheme(darkTheme = true) {
        ProfileView(ProfileViewState(user = previewUser, nameDraft = previewUser.displayName, isOnline = false), {})
    }
}
