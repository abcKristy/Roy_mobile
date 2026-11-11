package com.example.roy.presentation.components.preview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.components.SecondaryButton

@Preview(name = "Secondary button light")
@Composable
fun RoySecondaryButtonLightPreview() {
    RoyButtonPreviewSurface(darkTheme = false) { SecondaryButtonPreviewContent() }
}

@Preview(name = "Secondary button dark")
@Composable
fun RoySecondaryButtonDarkPreview() {
    RoyButtonPreviewSurface(darkTheme = true) { SecondaryButtonPreviewContent() }
}

@Composable
private fun SecondaryButtonPreviewContent() {
    var clicked by remember { mutableStateOf(false) }
    SecondaryButton(
        text = if (clicked) "Выполнено" else "Продолжить",
        onClick = { clicked = true },
        modifier = Modifier.fillMaxWidth(),
    )
    SecondaryButton(
        text = "Недоступно",
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        isEnabled = false,
    )
}
