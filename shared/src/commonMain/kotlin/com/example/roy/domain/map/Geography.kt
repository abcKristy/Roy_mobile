package com.example.roy.domain.map

data class Position(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be within WGS84 bounds" }
        require(longitude in -180.0..180.0) { "Longitude must be within WGS84 bounds" }
    }
}

/** A WGS84 rectangle that can be sent as a single Point API viewport. */
data class BoundingBox(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    init {
        require(west in -180.0..180.0 && east in -180.0..180.0 && west < east) {
            "Expected WGS84 longitudes with west < east"
        }
        require(south in -90.0..90.0 && north in -90.0..90.0 && south < north) {
            "Expected WGS84 latitudes with south < north"
        }
    }

    fun coordinatesInApiOrder(): List<Double> = listOf(west, south, east, north)
}
