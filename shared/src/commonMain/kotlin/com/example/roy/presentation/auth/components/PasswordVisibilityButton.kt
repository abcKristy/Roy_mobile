package com.example.roy.presentation.auth.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.painterResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_eye

@Composable
internal fun PasswordVisibilityButton(
    isVisible: Boolean,
    isEnabled: Boolean,
    showLabel: String,
    hideLabel: String,
    onToggle: () -> Unit,
) {
    val colors = RoyTheme.colors
    val dimensions = RoyTheme.dimensions
    Box(
        modifier = Modifier.size(dimensions.minimumTouchTarget).clickable(
            enabled = isEnabled,
            role = Role.Button,
            onClick = onToggle,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.auth_eye),
            contentDescription = if (isVisible) hideLabel else showLabel,
            modifier = Modifier.size(dimensions.iconSize),
            colorFilter = ColorFilter.tint(
                if (!isEnabled) colors.textDisabled else if (isVisible) colors.accent else colors.textSecondary,
            ),
        )
    }
}
