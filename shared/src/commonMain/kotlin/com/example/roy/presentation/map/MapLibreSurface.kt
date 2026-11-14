package com.example.roy.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.logging.MapLogLevel
import org.maplibre.compose.logging.MapLogSource
import org.maplibre.compose.logging.MapLogger
import org.maplibre.compose.logging.MapLogging
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.RenderStats
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DelicateMaplibreComposeApi
import org.maplibre.compose.util.VisibleBounds
import org.maplibre.spatialk.geojson.Position as LibrePosition

internal class MapLibreSurface(
    private val styleUrl: String,
) : MapSurface {
    @Composable
    override fun Content(
        state: MapSurfaceState,
        onViewportChanged: (BoundingBox) -> Unit,
        onPinSelected: (String) -> Unit,
        onMapTapped: (Position) -> Unit,
        onRenderStatusChanged: (MapRenderStatus) -> Unit,
        modifier: Modifier,
    ) {
        var useEmptyStyle by remember(styleUrl) { mutableStateOf(false) }
        val map = rememberMapState(
            baseStyle = if (useEmptyStyle) BaseStyle.Empty else BaseStyle.Uri(styleUrl),
            initialCameraPosition = state.initialCamera.toLibreCamera(),
        )
        val viewportChanged by rememberUpdatedState(onViewportChanged)
        val cameraSettled by rememberUpdatedState(state.onCameraSettled)
        val mapTapped by rememberUpdatedState(onMapTapped)
        val renderStatusChanged by rememberUpdatedState(onRenderStatusChanged)
        val interactions = remember {
            MapInteractions {
                callbacks {
                    click {
                        onEvent { event ->
                            val position = event.position
                            position?.let { mapTapped(Position(latitude = it.latitude, longitude = it.longitude)) }
                            ClickResult.Pass
                        }
                    }
                }
            }
        }

        LaunchedEffect(map) {
            snapshotFlow { map.viewport?.visibleBounds?.toRoyBounds()?.takeUnless { map.isCameraMoving } }
                .filterNotNull()
                .collect { bounds ->
                    val camera = map.cameraPosition
                    cameraSettled(MapCamera(Position(camera.center.latitude, camera.center.longitude), camera.zoom))
                    viewportChanged(bounds)
                }
        }
        val tileFailure = remember(map) { MutableStateFlow(false) }
        LaunchedEffect(map) {
            val generations = NativeTileFailures.generations
            val generationAtAttach = generations.value
            generations.collect { generation ->
                if (generation != generationAtAttach) tileFailure.value = true
            }
        }
        LaunchedEffect(map) {
            map.events.collect {
                if (it is MapEvent.FrameRendered && it.stats?.mode == RenderStats.Mode.Full) {
                    tileFailure.value = false
                }
            }
        }
        LaunchedEffect(map, tileFailure) {
            snapshotFlow {
                when (map.style.loadState) {
                    StyleLoadState.Ready -> MapRenderStatus.Ready
                    is StyleLoadState.Failed -> MapRenderStatus.Failed
                    else -> MapRenderStatus.Loading
                }
            }.combine(tileFailure) { style, hasTileFailure ->
                if (useEmptyStyle || hasTileFailure) MapRenderStatus.Failed else style
            }.collect { status ->
                if (map.style.loadState is StyleLoadState.Failed) useEmptyStyle = true
                renderStatusChanged(status)
            }
        }
        LaunchedEffect(map, state.cameraCommand?.requestId) {
            state.cameraCommand?.let {
                map.setCameraPosition(it.camera.toLibreCamera())
                state.onCameraApplied(it.requestId)
            }
        }

        MaplibreMap(modifier = modifier, state = map, interactions = interactions) {
            state.devicePosition?.let {
                MapDeviceMarker(Modifier.placedAt(it.toLibrePosition()))
            }
            state.markers.sortedBy { it.pointId == state.selectedPointId }.forEach { marker ->
                key(marker.pointId) {
                    val selected = marker.pointId == state.selectedPointId
                    val pinModifier = Modifier.placedAt(marker.position.toLibrePosition(), Alignment.BottomCenter)
                    val onClick = { onPinSelected(marker.pointId) }
                    if (marker.topicKeys.size > 1) {
                        MapMultiTopicPin(marker.topicKeys.size, selected, marker.label, onClick, pinModifier)
                    } else {
                        MapPointPin(marker.topicKeys.firstOrNull(), selected, marker.label, onClick, pinModifier)
                    }
                }
            }
        }
    }
}

@OptIn(DelicateMaplibreComposeApi::class)
private object NativeTileFailures {
    private val failures = MutableStateFlow(0L)
    val generations get() = failures

    init {
        val previous = MapLogging.logger
        // MapLibre exposes tile failures through a process-wide native diagnostic.
        MapLogging.logger = MapLogger { record ->
            if (record.source == MapLogSource.NativeEngine && record.level == MapLogLevel.Error &&
                record.category == "Style" && record.message.startsWith("Failed to load tile ")
            ) {
                failures.update { it + 1 }
            }
            if (previous != null && record.level >= previous.minLevel) previous.log(record)
        }
    }
}

private fun MapCamera.toLibreCamera() = CameraPosition(
    center = LibrePosition(longitude = center.longitude, latitude = center.latitude),
    zoom = zoom,
)

private fun VisibleBounds.toRoyBounds(): BoundingBox? {
    val wrapped = toBoundingBox()
    val south = south.coerceIn(-90.0, 90.0)
    val north = north.coerceIn(-90.0, 90.0)
    if (south >= north || west >= east) return null
    val west = wrapped.southwest.longitude
    val east = wrapped.northeast.longitude
    // The public API accepts one non-wrapping bbox. Keep both sides visible at the date line.
    return if (west < east) BoundingBox(west, south, east, north)
    else BoundingBox(-180.0, south, 180.0, north)
}

private fun Position.toLibrePosition() = LibrePosition(longitude = longitude, latitude = latitude)