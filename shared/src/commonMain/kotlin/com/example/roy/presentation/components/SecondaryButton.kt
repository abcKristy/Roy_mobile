package com.example.roy.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import com.example.roy.presentation.theme.RoyTheme

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    val colors = RoyTheme.colors
    RoyActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        isEnabled = isEnabled,
        isSubmitting = false,
        background = SolidColor(if (isEnabled) colors.glass else colors.secondary),
        textColor = if (isEnabled) colors.textPrimary else colors.textDisabled,
        outlineColor = if (isEnabled) colors.outline else null,
    )
}
