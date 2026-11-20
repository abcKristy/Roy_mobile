package com.example.roy.presentation.map

import com.example.roy.domain.location.DeviceLocationResult
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.Position
import com.example.roy.domain.map.ViewportQuery
import kotlin.time.Instant

internal data class MapViewState(
    val camera: MapCamera = MoscowCamera,
    val createPosition: Position? = null,
    val cameraCommand: MapCameraCommand? = null,
    val devicePosition: Position? = null,
    val isLocating: Boolean = false,
    val locationProblem: DeviceLocationResult? = null,
    val query: ViewportQuery? = null,
    val appliedFilter: PointFilter = PointFilter(),
    val viewport: ViewportContent = ViewportContent.Loading,
    val points: List<Point> = emptyList(),
    val selectedPointId: String? = null,
    val preview: PointPreview? = null,
    val renderStatus: MapRenderStatus = MapRenderStatus.Loading,
    val surfaceRevision: Int = 0,
    val isOnline: Boolean = true,
    val fetchedAt: Instant? = null,
    val isUsingCache: Boolean = false,
)
