package com.example.roy.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_attribution_openfreemap
import roy.shared.generated.resources.map_attribution_openmaptiles
import roy.shared.generated.resources.map_attribution_openstreetmap

@Composable
internal fun MapAttribution(onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = RoyTheme.colors
    val dimensions = RoyTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radii.small)

    FlowRow(
        modifier = modifier.clip(shape).background(colors.surfaceElevated),
    ) {
        for (link in AttributionLink.entries) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = dimensions.minimumTouchTarget, minHeight = dimensions.minimumTouchTarget)
                    .clickable { onOpenLink(link.url) }
                    .padding(horizontal = dimensions.spacing.extraSmall),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = stringResource(link.label),
                    style = RoyTheme.typography.caption.copy(
                        color = colors.textPrimary,
                        textDecoration = TextDecoration.Underline,
                    ),
                )
            }
        }
    }
}

private enum class AttributionLink(val label: StringResource, val url: String) {
    OpenFreeMap(Res.string.map_attribution_openfreemap, "https://openfreemap.org/"),
    OpenMapTiles(Res.string.map_attribution_openmaptiles, "https://openmaptiles.org/"),
    OpenStreetMap(Res.string.map_attribution_openstreetmap, "https://www.openstreetmap.org/copyright"),
}

@Preview(name = "Map attribution light")
@Composable
private fun MapAttributionLightPreview() {
    RoyTheme(darkTheme = false) { MapAttribution(onOpenLink = {}) }
}

@Preview(name = "Map attribution dark")
@Composable
private fun MapAttributionDarkPreview() {
    RoyTheme(darkTheme = true) { MapAttribution(onOpenLink = {}) }
}
