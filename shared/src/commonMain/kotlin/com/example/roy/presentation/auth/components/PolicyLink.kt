package com.example.roy.presentation.auth.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import com.example.roy.presentation.theme.RoyTheme

@Composable
internal fun PolicyLink(
    url: String?,
    isLoading: Boolean,
    isEnabled: Boolean,
    policyLabel: String,
    loadingLabel: String,
    retryLabel: String,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val documentUrl = url?.takeIf { it.isNotBlank() }
    val label = when {
        isLoading -> loadingLabel
        documentUrl != null -> policyLabel
        else -> retryLabel
    }
    val canClick = isEnabled && !isLoading
    Box(
        modifier.heightIn(min = RoyTheme.dimensions.minimumTouchTarget)
            .clickable(enabled = canClick, role = Role.Button) {
                if (documentUrl != null) onOpen(documentUrl) else onRetry()
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            text = label,
            style = RoyTheme.typography.bodySmall.copy(
                color = if (canClick) RoyTheme.colors.textPrimary else RoyTheme.colors.textSecondary,
                textDecoration = if (canClick) TextDecoration.Underline else TextDecoration.None,
            ),
        )
    }
}
