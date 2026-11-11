package com.example.roy.presentation.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.theme.RoyTheme

data class EmptyStateAction(val label: String, val onClick: () -> Unit)

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
    action: EmptyStateAction? = null,
) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val spacing = RoyTheme.dimensions.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.compact),
    ) {
        illustration?.invoke()
        BasicText(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = typography.headingSmall.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
        )
        BasicText(
            text = body,
            style = typography.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center),
        )
        action?.let {
            PrimaryButton(text = it.label, onClick = it.onClick, modifier = Modifier.fillMaxWidth())
        }
    }
}
