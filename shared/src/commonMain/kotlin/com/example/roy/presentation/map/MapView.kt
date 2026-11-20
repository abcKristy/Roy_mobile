package com.example.roy.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_close
import roy.shared.generated.resources.map_create_here
import roy.shared.generated.resources.map_detail_loading
import roy.shared.generated.resources.map_people
import roy.shared.generated.resources.protected_create

@Composable
internal fun MapView(
    viewState: MapViewState,
    markers: List<MapMarker>,
    eventHandler: (MapViewEvent) -> Unit,
) {
    val surface = LocalMapSurface.current
    val spacing = RoyTheme.dimensions.spacing
    val isOffline = !viewState.isOnline || viewState.isUsingCache
    Box(Modifier.fillMaxSize().background(RoyTheme.colors.background)) {
        key(surface, viewState.surfaceRevision) {
            surface.Content(
                state = MapSurfaceState(
                    initialCamera = viewState.camera,
                    cameraCommand = viewState.cameraCommand,
                    devicePosition = viewState.devicePosition,
                    createPosition = viewState.createPosition,
                    selectedPointId = viewState.selectedPointId,
                    markers = markers,
                    onCameraApplied = { eventHandler(MapViewEvent.OnCameraApplied(it)) },
                    onCameraSettled = { eventHandler(MapViewEvent.OnCameraSettled(it)) },
                ),
                onViewportChanged = { eventHandler(MapViewEvent.OnViewportChanged(it)) },
                onPinSelected = { eventHandler(MapViewEvent.OnPinSelected(it)) },
                onMapTapped = { eventHandler(MapViewEvent.OnMapTapped(it)) },
                onRenderStatusChanged = { eventHandler(MapViewEvent.OnRenderingChanged(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) {
                TertiaryButton(
                    label = stringResource(
                        if (viewState.createPosition == null) Res.string.protected_create else Res.string.map_create_here,
                    ),
                    onClick = { eventHandler(MapViewEvent.OnCreateRequested) },
                    isEnabled = !isOffline,
                )
                MapLocationButton(
                    isLocating = viewState.isLocating,
                    isEnabled = !viewState.isLocating,
                    onClick = { eventHandler(MapViewEvent.OnLocateRequested) },
                )
                MapFilterButton(viewState.appliedFilter.isActive) { eventHandler(MapViewEvent.OnOpenFilters) }
            }
            MapFeedback(viewState, eventHandler)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                when (val preview = viewState.preview) {
                    is PointPreview.Ready -> PointPreviewCard(
                        point = preview.point,
                        peopleLabel = pluralStringResource(Res.plurals.map_people, preview.point.peopleCount, preview.point.peopleCount),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                            PointDetailsButton(isEnabled = !isOffline) { eventHandler(MapViewEvent.OnDetailsRequested) }
                            TertiaryButton(stringResource(Res.string.map_close), { eventHandler(MapViewEvent.OnPreviewClosed) })
                        }
                    }
                    PointPreview.Loading -> RoyCard {
                        LoadingState(LoadingMode.Inline, stringResource(Res.string.map_detail_loading))
                        PointDetailsButton(isEnabled = false) {}
                        TertiaryButton(stringResource(Res.string.map_close), { eventHandler(MapViewEvent.OnPreviewClosed) })
                    }
                    is PointPreview.Failed -> RoyCard {
                        MapStatus(pointErrorLabel(preview.error), MapViewEvent.OnRetryPreview, eventHandler)
                        TertiaryButton(stringResource(Res.string.map_close), { eventHandler(MapViewEvent.OnPreviewClosed) })
                    }
                    null -> Unit
                }
            }
            if (!isOffline) MapAttribution(onOpenLink = { eventHandler(MapViewEvent.OnAttributionRequested(it)) })
        }
    }
}
