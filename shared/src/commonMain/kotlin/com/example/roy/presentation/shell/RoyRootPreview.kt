package com.example.roy.presentation.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.navigation.MainTab
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Roy shell light")
@Composable
private fun RoyRootLightPreview() {
    RoyTheme(darkTheme = false) { RootPreviewContent(MainTab.Map) }
}

@Preview(name = "Roy shell dark")
@Composable
private fun RoyRootDarkPreview() {
    RoyTheme(darkTheme = true) { RootPreviewContent(MainTab.Chats) }
}

@Composable
private fun RootPreviewContent(tab: MainTab) {
    RoyShellLayout(
        snackbar = remember { RoySnackbarHostState() },
        tabs = { RoyRootView(RoyRootViewState(tab), eventHandler = {}) },
        content = {},
    )
}
