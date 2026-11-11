package com.example.roy.presentation.components.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.overlays.RoySnackbarAction
import com.example.roy.presentation.components.overlays.RoySnackbarHost
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.components.overlays.RoySnackbarMessage
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Snackbar light")
@Composable
fun RoySnackbarLightPreview() {
    SnackbarPreviewContent(initialDark = false)
}

@Preview(name = "Snackbar dark")
@Composable
fun RoySnackbarDarkPreview() {
    SnackbarPreviewContent(initialDark = true)
}

@Composable
private fun SnackbarPreviewContent(initialDark: Boolean) {
    val state = remember { RoySnackbarHostState() }
    var dark by remember { mutableStateOf(initialDark) }
    var isActionHandled by remember { mutableStateOf(false) }
    val first = RoySnackbarMessage(1, "Нет точек по фильтру", durationMillis = 20_000)
    val second = RoySnackbarMessage(2, "Показано новое сообщение", durationMillis = 8_000)
    val action = RoySnackbarMessage(
        id = 3,
        text = "Фильтр применён",
        action = RoySnackbarAction("Сбросить") { isActionHandled = true },
        durationMillis = 20_000,
    )
    RoyTheme(darkTheme = dark) {
        val spacing = RoyTheme.dimensions.spacing
        val colors = RoyTheme.colors
        Column(Modifier.fillMaxSize().background(colors.background).safeContentPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    SecondaryButton("Показать", onClick = { state.show(first) })
                    SecondaryButton("Заменить", onClick = { state.show(second) })
                    SecondaryButton("Сменить тему", onClick = { dark = !dark })
                    SecondaryButton("Добавить действие", onClick = { state.show(action) })
                    if (isActionHandled) {
                        BasicText("Фильтр сброшен", style = RoyTheme.typography.body.copy(color = colors.textPrimary))
                    }
                }
                RoySnackbarHost(state, Modifier.align(Alignment.BottomCenter).padding(spacing.medium))
            }
            BasicText(
                "Панель вкладок",
                modifier = Modifier.fillMaxWidth().background(colors.surfaceElevated).padding(spacing.medium),
                style = RoyTheme.typography.caption.copy(color = colors.textPrimary),
            )
        }
    }
}
