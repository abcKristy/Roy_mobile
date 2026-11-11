package com.example.roy.presentation.components.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.components.fields.RoyTextField
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Text fields light")
@Composable
fun RoyTextFieldsLightPreview() {
    RoyTheme(darkTheme = false) { RoyTextFieldPreviewContent() }
}

@Preview(name = "Text fields dark")
@Composable
fun RoyTextFieldsDarkPreview() {
    RoyTheme(darkTheme = true) { RoyTextFieldPreviewContent() }
}

@Preview(name = "Text fields system theme")
@Composable
fun RoyTextFieldsSystemPreview() {
    RoyTheme { RoyTextFieldPreviewContent() }
}

@Composable
private fun RoyTextFieldPreviewContent() {
    var name by rememberSaveable { mutableStateOf("Анна") }
    var password by remember { mutableStateOf("пароль") }
    var age by rememberSaveable { mutableStateOf("11") }
    var isImeHandled by rememberSaveable { mutableStateOf(false) }
    val dimensions = RoyTheme.dimensions
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RoyTheme.colors.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(dimensions.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
    ) {
        RoyTextField(
            value = name,
            label = "Имя",
            onValueChange = { name = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            onImeAction = { isImeHandled = it == ImeAction.Next },
        )
        if (isImeHandled) {
            BasicText("Действие обработано", style = RoyTheme.typography.caption.copy(color = RoyTheme.colors.textPrimary))
        }
        RoyTextField(
            value = password,
            label = "Пароль",
            onValueChange = { password = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        RoyTextField(
            value = age,
            label = "Возраст",
            onValueChange = { age = it },
            errorText = "Укажите возраст от 12 лет. Проверьте, что в поле введено полное число.",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        RoyTextField(
            value = "+7 999 123-45-67",
            label = "Телефон",
            onValueChange = {},
            isEnabled = false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
    }
}
