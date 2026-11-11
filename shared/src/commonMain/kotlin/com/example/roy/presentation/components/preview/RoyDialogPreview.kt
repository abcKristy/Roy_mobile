package com.example.roy.presentation.components.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.overlays.RoyDialog
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Confirm dialog light")
@Composable
fun RoyDialogLightPreview() {
    RoyTheme(darkTheme = false) { DialogPreviewContent(isDestructive = false) }
}

@Preview(name = "Confirm dialog dark")
@Composable
fun RoyDialogDarkPreview() {
    RoyTheme(darkTheme = true) { DialogPreviewContent(isDestructive = true) }
}

@Composable
private fun DialogPreviewContent(isDestructive: Boolean) {
    var isOpen by remember { mutableStateOf(false) }
    var confirmations by remember { mutableIntStateOf(0) }
    var cancellations by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    val colors = RoyTheme.colors
    val spacing = RoyTheme.dimensions.spacing
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).safeContentPadding().padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        SecondaryButton("Открыть диалог", onClick = { isOpen = true })
        BasicText("Отмен: $cancellations", style = RoyTheme.typography.body.copy(color = colors.textPrimary))
        BasicText("Подтверждений: $confirmations", style = RoyTheme.typography.body.copy(color = colors.textPrimary))
    }
    if (isOpen) {
        RoyDialog(
            title = "Удалить встречу?",
            body = "Участники больше не увидят встречу в списке.",
            confirmText = "Удалить",
            cancelText = "Отмена",
            onConfirm = {
                confirmations++
                isSubmitting = true
            },
            onCancel = {
                cancellations++
                isOpen = false
            },
            isSubmitting = isSubmitting,
            isDestructive = isDestructive,
        )
    }
}
