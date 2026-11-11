package com.example.roy.presentation.components.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Cards light")
@Composable
fun RoyCardsLightPreview() {
    RoyTheme(darkTheme = false) { RoyCardPreviewContent() }
}

@Preview(name = "Cards dark")
@Composable
fun RoyCardsDarkPreview() {
    RoyTheme(darkTheme = true) { RoyCardPreviewContent() }
}

@Composable
private fun RoyCardPreviewContent() {
    var isSelected by remember { mutableStateOf(false) }
    val spacing = RoyTheme.dimensions.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RoyTheme.colors.background)
            .safeContentPadding()
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        RoyCard(
            modifier = Modifier.fillMaxWidth(),
            isSelected = isSelected,
            onClick = { isSelected = !isSelected },
        ) {
            BasicText("Локация", style = RoyTheme.typography.headingSmall.copy(color = RoyTheme.colors.textPrimary))
            BasicText("Все темы на локации", style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.textSecondary))
        }
        RoyCard(modifier = Modifier.fillMaxWidth()) {
            BasicText("Описание", style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textPrimary))
        }
    }
}
