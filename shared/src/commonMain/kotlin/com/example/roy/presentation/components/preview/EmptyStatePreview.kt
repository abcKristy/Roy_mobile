package com.example.roy.presentation.components.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.components.feedback.EmptyState
import com.example.roy.presentation.components.feedback.EmptyStateAction
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.empty_chats_body
import roy.shared.generated.resources.empty_chats_open_map
import roy.shared.generated.resources.empty_chats_title
import roy.shared.generated.resources.empty_courtyard_dark
import roy.shared.generated.resources.empty_courtyard_light

@Preview(name = "Empty states light")
@Composable
fun RoyEmptyLightPreview() {
    RoyTheme(darkTheme = false) { EmptyPreviewContent(Res.drawable.empty_courtyard_light) }
}

@Preview(name = "Empty states dark")
@Composable
fun RoyEmptyDarkPreview() {
    RoyTheme(darkTheme = true) { EmptyPreviewContent(Res.drawable.empty_courtyard_dark) }
}

@Composable
private fun EmptyPreviewContent(courtyard: DrawableResource) {
    var isActionHandled by remember { mutableStateOf(false) }
    val spacing = RoyTheme.dimensions.spacing
    Column(
        modifier = Modifier.fillMaxSize().background(RoyTheme.colors.background)
            .safeContentPadding().verticalScroll(rememberScrollState()).padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.large),
    ) {
        EmptyState(
            title = stringResource(Res.string.empty_chats_title),
            body = stringResource(Res.string.empty_chats_body),
            illustration = {
                Image(
                    painter = painterResource(courtyard),
                    contentDescription = null,
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(300f / 248f),
                )
            },
            action = EmptyStateAction(stringResource(Res.string.empty_chats_open_map)) { isActionHandled = true },
        )
        EmptyState(title = "Нет уведомлений", body = "Новые уведомления появятся здесь")
        if (isActionHandled) {
            BasicText("Переход обработан", style = RoyTheme.typography.caption.copy(color = RoyTheme.colors.textPrimary))
        }
    }
}
