package com.example.roy.presentation.meetings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Meetings light")
@Composable
private fun MeetingLightPreview() {
    RoyTheme(darkTheme = false) {
        MeetingDialogs(MeetingViewState(sheet = MeetingSheet.List, canCreate = true), {})
    }
}

@Preview(name = "Meetings dark")
@Composable
private fun MeetingDarkPreview() {
    RoyTheme(darkTheme = true) {
        MeetingDialogs(MeetingViewState(sheet = MeetingSheet.List, canCreate = true), {})
    }
}
