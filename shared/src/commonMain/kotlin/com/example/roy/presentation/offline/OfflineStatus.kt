package com.example.roy.presentation.offline

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.core.formatLocalDateTime
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.offline_fetched_at
import roy.shared.generated.resources.offline_read_only
import kotlin.time.Instant

@Composable
internal fun OfflineStatus(fetchedAt: Instant?) {
    val style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.textPrimary)
    RoyCard {
        BasicText(
            stringResource(Res.string.offline_read_only),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = style,
        )
        fetchedAt?.let { BasicText(stringResource(Res.string.offline_fetched_at, formatLocalDateTime(it)), style = style) }
    }
}
