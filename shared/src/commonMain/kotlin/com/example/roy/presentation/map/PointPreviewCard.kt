package com.example.roy.presentation.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.roy.domain.map.Point
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun PointPreviewCard(
    point: Point,
    peopleLabel: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val spacing = RoyTheme.dimensions.spacing
    RoyCard(modifier = modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        BasicText(
            text = point.address,
            modifier = Modifier.semantics { heading() },
            style = typography.headingMedium.copy(color = colors.textPrimary),
        )
        BasicText(peopleLabel, style = typography.bodySmall.copy(color = colors.textSecondary))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            for (topic in point.topics.take(6)) {
                Image(
                    painter = painterResource(mapTopicIcon(topic.presetKey)),
                    contentDescription = topic.label,
                    modifier = Modifier.size(RoyTheme.dimensions.iconSize),
                    colorFilter = ColorFilter.tint(colors.textPrimary),
                )
            }
            if (point.topics.size > 6) {
                BasicText("+${point.topics.size - 6}", style = typography.bodySmall.copy(color = colors.textSecondary))
            }
        }
        actions()
    }
}
