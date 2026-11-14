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
import androidx.compose.ui.text.input.ImeAction
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
    var password by remember { mutableStateOf("demo-password") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Анна") }
    var age by remember { mutableStateOf("11") }
    val spacing = RoyTheme.dimensions.spacing

    Column(
        modifier = Modifier.background(RoyTheme.colors.background).padding(spacing.comfortable),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        DisplayNameField(
            value = name,
            label = "Имя",
            errorText = null,
            isEnabled = true,
            onValueChange = { name = it },
            onImeNext = {},
        )
        PhoneField(
            value = phone,
            label = "Телефон",
            contentDescription = "Номер телефона",
            errorText = "Этот номер уже используется",
            isEnabled = true,
            onValueChange = { phone = it },
            onImeNext = {},
        )
        PasswordField(
            value = password,
            label = "Пароль",
            errorText = null,
            isEnabled = true,
            imeAction = ImeAction.Done,
            onValueChange = { password = it },
            onImeAction = {},
            isVisible = isPasswordVisible,
            visibilityButton = {
                PasswordVisibilityButton(
                    isVisible = isPasswordVisible,
                    isEnabled = true,
                    showLabel = "Показать пароль",
                    hideLabel = "Скрыть пароль",
                    onToggle = { isPasswordVisible = !isPasswordVisible },
                )
            },
        )
        AgeField(
            ageText = age,
            label = "Возраст",
            errorText = "Укажите возраст от 12 лет",
            isEnabled = true,
            imeAction = ImeAction.Done,
            onValueChange = { age = it },
            onImeAction = {},
        )
    }
}
