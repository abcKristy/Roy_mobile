package com.example.roy.presentation.auth.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Auth fields light")
@Composable
internal fun AuthFieldsLightPreview() {
    RoyTheme(darkTheme = false) { AuthFieldsPreviewContent() }
}

@Preview(name = "Auth fields dark")
@Composable
internal fun AuthFieldsDarkPreview() {
    RoyTheme(darkTheme = true) { AuthFieldsPreviewContent() }
}

@Composable
private fun AuthFieldsPreviewContent() {
    var phone by remember { mutableStateOf("+79990000001") }
    val spacing = RoyTheme.dimensions.spacing

    Column(
        modifier = Modifier.background(RoyTheme.colors.background).padding(spacing.comfortable),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        PhoneField(
            value = phone,
            label = "Телефон",
            contentDescription = "Номер телефона",
            errorText = "Этот номер уже используется",
            isEnabled = true,
            onValueChange = { phone = it },
            onImeNext = {},
        )
    }
}
