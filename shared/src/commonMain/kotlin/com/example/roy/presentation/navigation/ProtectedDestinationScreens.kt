package com.example.roy.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.protected_back
import roy.shared.generated.resources.protected_open_chat
import roy.shared.generated.resources.protected_topic_prepared

@Composable
internal fun TopicChatHandoff(onBack: () -> Unit) {
    val spacing = RoyTheme.dimensions.spacing
    val textColor = RoyTheme.colors.textPrimary
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        TertiaryButton(stringResource(Res.string.protected_back), onBack)
        BasicText(
            stringResource(Res.string.protected_open_chat),
            modifier = Modifier.semantics { heading() },
            style = RoyTheme.typography.headingMedium.copy(color = textColor),
        )
        BasicText(
            stringResource(Res.string.protected_topic_prepared),
            style = RoyTheme.typography.body.copy(color = textColor),
        )
    }
}
