package com.example.roy

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.app_name

@Composable
@Preview
fun App() {
    val darkTheme = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (darkTheme) Color(0xFF121212) else Color.White)
            .safeContentPadding(),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = stringResource(Res.string.app_name),
            modifier = Modifier.semantics { heading() },
            style = TextStyle(color = if (darkTheme) Color.White else Color.Black),
        )
    }
}
