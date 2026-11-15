package com.example.roy.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.theme.RoyTheme

@Preview
@Composable
private fun TopicChatHandoffLightPreview() {
    RoyTheme(darkTheme = false) { TopicChatHandoff(onBack = {}) }
}

@Preview
@Composable
private fun TopicChatHandoffDarkPreview() {
    RoyTheme(darkTheme = true) { TopicChatHandoff(onBack = {}) }
}
