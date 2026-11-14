package com.example.roy.presentation.auth.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.roy.presentation.components.TertiaryButton

@Composable
internal fun AuthModeSwitch(
    label: String,
    isEnabled: Boolean,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TertiaryButton(
        label = label,
        onClick = onSwitch,
        modifier = modifier,
        isEnabled = isEnabled,
    )
}
