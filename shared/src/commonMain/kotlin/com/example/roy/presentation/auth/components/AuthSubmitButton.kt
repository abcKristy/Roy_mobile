package com.example.roy.presentation.auth.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.roy.presentation.components.PrimaryButton

@Composable
internal fun AuthSubmitButton(
    text: String,
    isEnabled: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryButton(
        text = text,
        onClick = onSubmit,
        modifier = modifier.fillMaxWidth(),
        isEnabled = isEnabled && !isSubmitting,
        isSubmitting = isSubmitting,
    )
}
