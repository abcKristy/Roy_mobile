package com.example.roy.presentation.components.preview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.components.DestructiveButton

@Preview(name = "Destructive button light")
@Composable
fun RoyDestructiveButtonLightPreview() {
    RoyButtonPreviewSurface(darkTheme = false) { DestructiveButtonPreviewContent() }
}

@Preview(name = "Destructive button dark")
@Composable
fun RoyDestructiveButtonDarkPreview() {
    RoyButtonPreviewSurface(darkTheme = true) { DestructiveButtonPreviewContent() }
}

@Composable
private fun DestructiveButtonPreviewContent() {
    var isSubmitting by remember { mutableStateOf(false) }
    DestructiveButton(
        label = "Выйти",
        onClick = { isSubmitting = !isSubmitting },
        modifier = Modifier.fillMaxWidth(),
        isSubmitting = isSubmitting,
    )
    DestructiveButton(
        label = "Недоступно",
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        isEnabled = false,
    )
}
