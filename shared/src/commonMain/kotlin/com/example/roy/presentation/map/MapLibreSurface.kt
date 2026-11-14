package com.example.roy.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.Position
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DelicateMaplibreComposeApi
import org.maplibre.compose.util.VisibleBounds
import org.maplibre.spatialk.geojson.Position as LibrePosition

internal class MapLibreSurface(
    private val styleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
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
        val map = rememberMapState(
            baseStyle = BaseStyle.Uri(styleUrl),
            initialCameraPosition = state.initialCamera.toLibreCamera(),
        )
        val viewportChanged by rememberUpdatedState(onViewportChanged)
        val mapTapped by rememberUpdatedState(onMapTapped)
        val renderStatusChanged by rememberUpdatedState(onRenderStatusChanged)
        val interactions = remember {
            MapInteractions {
                callbacks {
                    click {
                        onEvent { event ->
                            val position = event.position
                            if (position == null) {
                                ClickResult.Pass
                            } else {
                                mapTapped(Position(latitude = position.latitude, longitude = position.longitude))
                                ClickResult.Consume
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(map) {
            snapshotFlow { map.viewport?.visibleBounds?.toRoyBounds()?.takeUnless { map.isCameraMoving } }
                .filterNotNull()
                .collect { viewportChanged(it) }
        }
        val tileFailure = rememberTileFailures()
        LaunchedEffect(map) {
            map.events.collect {
                if (it is MapEvent.FrameRendered && it.stats?.mode == org.maplibre.compose.map.RenderStats.Mode.Full) {
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
                if (hasTileFailure) MapRenderStatus.Failed else style
            }.collect { renderStatusChanged(it) }
        }
        LaunchedEffect(map, state.cameraCommand?.requestId) {
            state.cameraCommand?.let { map.animateCamera(it.camera.toLibreCamera().toCameraUpdate()) }
        }

        MaplibreMap(modifier = modifier, state = map, interactions = interactions)
    }
}

@OptIn(DelicateMaplibreComposeApi::class)
@Composable
private fun rememberTileFailures(): MutableStateFlow<Boolean> {
    val failure = remember { MutableStateFlow(false) }
    DisposableEffect(failure) {
        val previous = MapLogging.logger
        // Native reports tile errors as diagnostics. Style failures use its separate typed state.
        val observer = MapLogger { record ->
            if (record.source == MapLogSource.NativeEngine && record.level == MapLogLevel.Error &&
                record.category == "Style" && record.message.startsWith("Failed to load tile ")
            ) {
                failure.value = true
            }
            if (previous != null && record.level >= previous.minLevel) previous.log(record)
        }
        MapLogging.logger = observer
        onDispose { if (MapLogging.logger === observer) MapLogging.logger = previous }
    }
    return failure
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
