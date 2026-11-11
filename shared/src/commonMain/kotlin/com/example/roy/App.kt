package com.example.roy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.app_name

@Composable
fun App() {
    RoyTheme { RoyAppContent() }
}

@Composable
private fun RoyAppContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RoyTheme.colors.background)
            .safeContentPadding()
            .padding(RoyTheme.dimensions.spacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = stringResource(Res.string.app_name),
            modifier = Modifier.semantics { heading() },
            style = RoyTheme.typography.headingLarge.copy(color = RoyTheme.colors.textPrimary),
        )
    }
}

@Preview(name = "Light")
@Composable
private fun RoyAppLightPreview() {
    RoyTheme(darkTheme = false) { RoyAppContent() }
}

@Preview(name = "Dark")
@Composable
private fun RoyAppDarkPreview() {
    RoyTheme(darkTheme = true) { RoyAppContent() }
}
