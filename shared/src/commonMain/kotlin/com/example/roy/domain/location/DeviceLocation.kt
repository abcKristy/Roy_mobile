package com.example.roy.domain.location

import com.example.roy.domain.map.Position

/** A single request initiated by the user's location action. */
internal fun interface DeviceLocation {
    suspend fun locate(): DeviceLocationResult

    /** Checks access for an existing fix without requesting permission or location. */
    fun accessProblem(): DeviceLocationResult? = null
}

internal sealed interface DeviceLocationResult {
    data class Located(val position: Position) : DeviceLocationResult
    data object Denied : DeviceLocationResult
    data object Restricted : DeviceLocationResult
    data object Disabled : DeviceLocationResult
    data object Unavailable : DeviceLocationResult
}
