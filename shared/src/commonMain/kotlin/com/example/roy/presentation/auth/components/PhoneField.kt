package com.example.roy.presentation.auth.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.roy.presentation.components.fields.RoyTextField

@Composable
internal fun PhoneField(
    value: String,
    label: String,
    contentDescription: String,
    errorText: String?,
    isEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onImeNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoyTextField(
        value = value,
        label = label,
        contentDescription = contentDescription,
        onValueChange = onValueChange,
        modifier = modifier,
        errorText = errorText,
        isEnabled = isEnabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next,
        ),
        onImeAction = { onImeNext() },
    )
}
