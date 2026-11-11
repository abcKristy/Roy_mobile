package com.example.roy.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import com.example.roy.presentation.theme.RoyTheme

@Composable
fun DestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isSubmitting: Boolean = false,
) {
    val colors = RoyTheme.colors
    val showActiveStyle = isEnabled || isSubmitting
    RoyActionButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        isEnabled = isEnabled,
        isSubmitting = isSubmitting,
        background = SolidColor(if (showActiveStyle) colors.destructiveActionBackground else colors.secondary),
        textColor = if (showActiveStyle) colors.destructiveActionContent else colors.textDisabled,
    )
}
