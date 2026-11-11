package com.example.roy.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import com.example.roy.presentation.theme.RoyTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isSubmitting: Boolean = false,
) {
    val colors = RoyTheme.colors
    val gradient = colors.primaryGradient
    val showActiveStyle = isEnabled || isSubmitting
    RoyActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        isEnabled = isEnabled,
        isSubmitting = isSubmitting,
        background = if (showActiveStyle) {
            Brush.horizontalGradient(listOf(gradient.start, gradient.end))
        } else {
            SolidColor(colors.secondary)
        },
        textColor = if (showActiveStyle) colors.primaryActionContent else colors.textDisabled,
    )
}
