package com.example.roy.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Tertiary button light")
@Composable
fun RoyTertiaryButtonLightPreview() {
    RoyButtonPreviewSurface(darkTheme = false) { TertiaryButtonPreviewContent() }
}

@Preview(name = "Tertiary button dark")
@Composable
fun RoyTertiaryButtonDarkPreview() {
    RoyButtonPreviewSurface(darkTheme = true) { TertiaryButtonPreviewContent() }
}

@Composable
private fun TertiaryButtonPreviewContent() {
    var clicked by remember { mutableStateOf(false) }
    TertiaryButton(label = if (clicked) "Выполнено" else "Да", onClick = { clicked = true })
    TertiaryButton(label = "Недоступно", onClick = {}, isEnabled = false)
}
