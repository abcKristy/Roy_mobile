package com.example.roy.presentation.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.style.TextAlign
import com.example.roy.presentation.components.indicators.RoyLoadingIndicator
import com.example.roy.presentation.theme.RoyTheme

enum class LoadingMode { Inline, Fullscreen }

@Composable
fun LoadingState(
    mode: LoadingMode,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val dimensions = RoyTheme.dimensions
    val semantics = Modifier.clearAndSetSemantics {
        this.contentDescription = contentDescription
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
    when (mode) {
        LoadingMode.Inline -> Row(
            modifier = modifier.then(semantics),
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoyLoadingIndicator(colors.primaryGradient.start, Modifier.size(dimensions.spacing.medium))
            BasicText(contentDescription, style = typography.bodySmall.copy(color = colors.textPrimary))
        }
        LoadingMode.Fullscreen -> Column(
            modifier = modifier.fillMaxSize().then(semantics).padding(dimensions.spacing.comfortable),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimensions.spacing.medium, Alignment.CenterVertically),
        ) {
            RoyLoadingIndicator(colors.primaryGradient.start, Modifier.size(dimensions.iconSize))
            BasicText(
                contentDescription,
                style = typography.headingSmall.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
            )
        }
    }
}
