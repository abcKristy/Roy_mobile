package com.example.roy.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Primary button light")
@Composable
fun RoyPrimaryButtonLightPreview() {
    RoyTheme(darkTheme = false) { PrimaryButtonPreviewContent() }
}

@Preview(name = "Primary button dark")
@Composable
fun RoyPrimaryButtonDarkPreview() {
    RoyTheme(darkTheme = true) { PrimaryButtonPreviewContent() }
}

@Composable
private fun PrimaryButtonPreviewContent() {
    var isSubmitting by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RoyTheme.colors.background)
            .safeContentPadding()
            .padding(RoyTheme.dimensions.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.small),
    ) {
        PrimaryButton(
            text = "Продолжить",
            onClick = { isSubmitting = !isSubmitting },
            modifier = Modifier.fillMaxWidth(),
            isSubmitting = isSubmitting,
        )
        PrimaryButton(
            text = "Недоступно",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            isEnabled = false,
        )
    }
}
