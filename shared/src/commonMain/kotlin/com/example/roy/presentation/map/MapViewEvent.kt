package com.example.roy.presentation.map

import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.Position

internal sealed class MapViewEvent {
    data class OnAttributionRequested(val url: String) : MapViewEvent()
    data class OnViewportChanged(val bounds: BoundingBox) : MapViewEvent()
    data object OnOpenFilters : MapViewEvent()
    data class OnFilterChanged(val filter: PointFilter) : MapViewEvent()
    data class OnPointChanged(val pointId: String, val isGone: Boolean) : MapViewEvent()
    data class OnPinSelected(val pointId: String) : MapViewEvent()
    data class OnMapTapped(val position: Position) : MapViewEvent()
    data object OnCreateRequested : MapViewEvent()
    data object OnCreateNavigationDeclined : MapViewEvent()
    data class OnRenderingChanged(val status: MapRenderStatus) : MapViewEvent()
    data class OnCameraApplied(val requestId: Long) : MapViewEvent()
    data class OnCameraSettled(val camera: MapCamera) : MapViewEvent()
    data object OnLocateRequested : MapViewEvent()
    data object OnScreenResumed : MapViewEvent()
    data object OnScreenLeft : MapViewEvent()
    data object OnPreviewClosed : MapViewEvent()
    data object OnDetailsRequested : MapViewEvent()
    data object OnRetryViewport : MapViewEvent()
    data object OnRetryPreview : MapViewEvent()
    data object OnRetryBasemap : MapViewEvent()
}
