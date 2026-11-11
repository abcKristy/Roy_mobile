package com.example.roy.presentation.components.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Error state light")
@Composable
fun RoyErrorLightPreview() {
    RoyTheme(darkTheme = false) { ErrorPreviewContent() }
}

@Preview(name = "Error state dark")
@Composable
fun RoyErrorDarkPreview() {
    RoyTheme(darkTheme = true) { ErrorPreviewContent() }
}

@Composable
private fun ErrorPreviewContent() {
    var isRetrying by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().background(RoyTheme.colors.background).safeContentPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.medium),
    ) {
        ErrorState(
            safeMessage = "Проверьте подключение. Попробуйте ещё раз, когда сеть станет доступна.",
            isRetryEnabled = !isRetrying,
            onRetry = { isRetrying = !isRetrying },
        )
        if (isRetrying) {
            BasicText("Запрос отправлен", style = RoyTheme.typography.caption.copy(color = RoyTheme.colors.textPrimary))
        }
    }
}
