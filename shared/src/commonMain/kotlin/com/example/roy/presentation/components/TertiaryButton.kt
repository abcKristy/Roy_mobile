package com.example.roy.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.roy.presentation.theme.RoyTheme

@Composable
fun TertiaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    val colors = RoyTheme.colors
    RoyActionButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        isEnabled = isEnabled,
        isSubmitting = false,
        background = null,
        textColor = if (isEnabled) colors.textPrimary else colors.textDisabled,
    )
}
