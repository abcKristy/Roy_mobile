package com.example.roy.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Жалоба — светлая тема")
@Composable
private fun ChatReportLightPreview() = ChatReportPreview(false)

@Preview(name = "Жалоба — тёмная тема")
@Composable
private fun ChatReportDarkPreview() = ChatReportPreview(true)

@Composable
private fun ChatReportPreview(darkTheme: Boolean) {
    RoyTheme(darkTheme = darkTheme) {
        ChatReportView(ChatReportTarget("participant", "Участник"), ChatReportState(), eventHandler = {})
    }
}
