package com.example.roy.presentation.auth.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.roy.presentation.components.fields.RoyTextField

@Composable
internal actual fun PhoneField(
    value: String,
    label: String,
    contentDescription: String,
    errorText: String?,
    isEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onImeNext: () -> Unit,
    modifier: Modifier,
    focusRequest: Int,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest != 0) focusRequester.requestFocus()
    }
    RoyTextField(
        value = value,
        label = label,
        contentDescription = contentDescription,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focusRequester),
        errorText = errorText,
        isEnabled = isEnabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next,
        ),
        onImeAction = { onImeNext() },
    )
}
