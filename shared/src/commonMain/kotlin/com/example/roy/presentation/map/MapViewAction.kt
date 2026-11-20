package com.example.roy.presentation.map

import com.example.roy.domain.map.Position

internal sealed class MapViewAction {
    data class OpenAttribution(val url: String) : MapViewAction()
    data object OpenFilters : MapViewAction()
    data class OpenDetails(val pointId: String) : MapViewAction()
    data class OpenCreate(val position: Position) : MapViewAction()
    data object PointGone : MapViewAction()
}
