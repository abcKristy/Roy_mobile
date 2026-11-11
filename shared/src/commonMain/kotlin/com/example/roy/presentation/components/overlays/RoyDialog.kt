package com.example.roy.presentation.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.roy.presentation.components.DestructiveButton
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.theme.RoyTheme

@Composable
fun RoyDialog(
    title: String,
    body: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    isSubmitting: Boolean = false,
    isDestructive: Boolean = false,
    dismissOnOutsideClick: Boolean = false,
) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val dimensions = RoyTheme.dimensions
    Dialog(
        onDismissRequest = { if (!isSubmitting) onCancel() },
        properties = DialogProperties(
            dismissOnBackPress = !isSubmitting,
            dismissOnClickOutside = dismissOnOutsideClick && !isSubmitting,
        ),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(colors.surfaceElevated, RoundedCornerShape(dimensions.radii.dialog))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacing.compact),
        ) {
            BasicText(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = typography.headingSmall.copy(color = colors.textPrimary),
            )
            BasicText(text = body, style = typography.body.copy(color = colors.textSecondary))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = cancelText,
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    isEnabled = !isSubmitting,
                )
                if (isDestructive) {
                    DestructiveButton(
                        label = confirmText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        isSubmitting = isSubmitting,
                    )
                } else {
                    PrimaryButton(
                        text = confirmText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        isSubmitting = isSubmitting,
                    )
                }
            }
        }
    }
}
