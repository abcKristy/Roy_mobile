package com.example.roy.presentation.auth.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.roy.presentation.components.fields.RoyTextField

@Composable
internal fun PasswordField(
    value: String,
    label: String,
    errorText: String?,
    isEnabled: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = false,
    visibilityButton: (@Composable () -> Unit)? = null,
) {
    RoyTextField(
        value = value,
        label = label,
        onValueChange = onValueChange,
        modifier = modifier,
        errorText = errorText,
        isEnabled = isEnabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        onImeAction = { onImeAction() },
        trailingContent = visibilityButton,
    )
}
