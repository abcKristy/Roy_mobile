package com.example.roy.presentation.auth.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun PhoneField(
    value: String,
    label: String,
    contentDescription: String,
    errorText: String?,
    isEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onImeNext: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequest: Int = 0,
)
