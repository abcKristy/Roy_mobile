package com.example.roy.presentation.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.feedback_error_title
import roy.shared.generated.resources.feedback_retry

@Composable
fun ErrorState(
    safeMessage: String,
    isRetryEnabled: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val spacing = RoyTheme.dimensions.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.comfortable),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            BasicText(
                text = stringResource(Res.string.feedback_error_title),
                modifier = Modifier.semantics { heading() },
                style = typography.headingMedium.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
            )
            BasicText(
                text = safeMessage,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = typography.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center),
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.feedback_retry),
            onClick = onRetry,
            isEnabled = isRetryEnabled,
        )
    }
}
