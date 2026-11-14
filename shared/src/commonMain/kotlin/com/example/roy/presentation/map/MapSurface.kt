package com.example.roy.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.Position

/** Shared UI port implemented by the map renderer. Viewports are reported when the camera settles. */
interface MapSurface {
    @Composable
    fun Content(
        state: MapSurfaceState,
        onViewportChanged: (BoundingBox) -> Unit,
        onPinSelected: (String) -> Unit,
        onMapTapped: (Position) -> Unit,
        onRenderStatusChanged: (MapRenderStatus) -> Unit,
        modifier: Modifier = Modifier,
    )
}

data class MapSurfaceState(
    val initialCamera: MapCamera,
    val cameraCommand: MapCameraCommand? = null,
    val markers: List<MapMarker> = emptyList(),
    val selectedPointId: String? = null,
)

data class MapCamera(
    val center: Position,
    val zoom: Double,
)

/** A request ID identifies a camera movement across recompositions. */
data class MapCameraCommand(
    val requestId: Long,
    val camera: MapCamera,
)

enum class MapRenderStatus {
    Loading,
    Ready,
    Failed,
}

/** The backend Point ID remains the marker key across updates. */
data class MapMarker(
    val pointId: String,
    val position: Position,
) {
    init {
        require(pointId.isNotBlank()) { "Point ID must not be blank" }
    }
}
