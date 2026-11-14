package com.example.roy.presentation.map

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.roy.domain.location.DeviceLocationResult
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointFilter
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.*

@Composable
internal fun MapFeedback(state: MapViewState, eventHandler: (MapViewEvent) -> Unit) {
    if (state.renderStatus == MapRenderStatus.Failed) {
        MapStatus(stringResource(Res.string.map_basemap_failed), MapViewEvent.RetryBasemap, eventHandler)
    }
    when (val content = state.viewport) {
        ViewportContent.Loading -> if (state.points.isEmpty() && (state.query != null || state.renderStatus != MapRenderStatus.Failed)) {
            RoyCard { LoadingState(LoadingMode.Inline, stringResource(Res.string.map_loading)) }
        }
        ViewportContent.Ready -> if (state.points.isEmpty() && state.renderStatus != MapRenderStatus.Failed) {
            val empty = if (state.query?.filter?.isActive == true) Res.string.map_filter_empty else Res.string.map_empty
            RoyCard {
                BasicText(stringResource(empty), style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.textPrimary))
                if (state.query?.filter?.isActive == true) {
                    TertiaryButton(stringResource(Res.string.map_reset_filter), {
                        eventHandler(MapViewEvent.FilterChanged(PointFilter()))
                    })
                }
            }
        }
        is ViewportContent.Failed -> MapStatus(pointErrorLabel(content.error), MapViewEvent.RetryViewport, eventHandler)
    }
    val location = when (state.locationProblem) {
        DeviceLocationResult.Denied -> Res.string.map_location_denied
        DeviceLocationResult.Restricted -> Res.string.map_location_restricted
        DeviceLocationResult.Disabled -> Res.string.map_location_disabled
        DeviceLocationResult.Unavailable -> Res.string.map_location_unavailable
        else -> null
    }
    if (location != null) MapStatus(stringResource(location), null, eventHandler)
}

@Composable
internal fun MapStatus(text: String, retry: MapViewEvent?, eventHandler: (MapViewEvent) -> Unit) {
    RoyCard {
        BasicText(
            text,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.textPrimary),
        )
        if (retry != null) TertiaryButton(stringResource(Res.string.map_retry), { eventHandler(retry) })
    }
}

@Composable
internal fun pointErrorLabel(error: PointError): String =
    stringResource(if (error == PointError.Network) Res.string.map_offline else Res.string.map_api_failed)
