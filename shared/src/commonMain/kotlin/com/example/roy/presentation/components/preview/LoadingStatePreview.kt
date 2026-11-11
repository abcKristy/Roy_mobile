package com.example.roy.presentation.components.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Loading states light")
@Composable
fun RoyLoadingLightPreview() {
    RoyTheme(darkTheme = false) { LoadingPreviewContent() }
}

@Preview(name = "Loading states dark")
@Composable
fun RoyLoadingDarkPreview() {
    RoyTheme(darkTheme = true) { LoadingPreviewContent() }
}

@Composable
private fun LoadingPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth().background(RoyTheme.colors.background)
            .safeContentPadding().padding(RoyTheme.dimensions.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.medium),
    ) {
        LoadingState(LoadingMode.Inline, "Загружаем встречи рядом")
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            LoadingState(LoadingMode.Fullscreen, "Загружаем карту")
        }
    }
}
