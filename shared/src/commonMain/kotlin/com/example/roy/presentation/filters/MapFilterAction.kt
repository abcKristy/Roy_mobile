package com.example.roy.presentation.filters

import com.example.roy.domain.map.PointFilter

internal sealed class MapFilterAction {
    data class Applied(val filter: PointFilter) : MapFilterAction()
    data object Back : MapFilterAction()
}
