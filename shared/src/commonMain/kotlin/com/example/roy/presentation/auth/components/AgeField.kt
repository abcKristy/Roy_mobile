package com.example.roy.presentation.auth.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.roy.presentation.components.fields.RoyTextField

@Composable
internal fun AgeField(
    ageText: String,
    label: String,
    errorText: String?,
    isEnabled: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoyTextField(
        value = ageText,
        label = label,
        onValueChange = onValueChange,
        modifier = modifier,
        errorText = errorText,
        isEnabled = isEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        onImeAction = { onImeAction() },
    )
}
