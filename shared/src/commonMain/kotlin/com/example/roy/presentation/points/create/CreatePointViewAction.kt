package com.example.roy.presentation.points.create

import com.example.roy.domain.points.CreatedPointDestination

internal sealed class CreatePointViewAction {
    data object Close : CreatePointViewAction()
    data object SessionExpired : CreatePointViewAction()
    data class Created(val destination: CreatedPointDestination) : CreatePointViewAction()
}
