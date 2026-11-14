package com.example.roy.presentation.map

import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MapSurfaceContractTest {
    @Test
    fun bboxUsesLongitudeLatitudeOrderForPointApi() {
        val box = BoundingBox(west = 37.5, south = 55.6, east = 37.8, north = 55.9)

        assertEquals(listOf(37.5, 55.6, 37.8, 55.9), box.coordinatesInApiOrder())
    }

    @Test
    fun bboxAcceptsFullWgs84Bounds() {
        val box = BoundingBox(west = -180.0, south = -90.0, east = 180.0, north = 90.0)

        assertEquals(listOf(-180.0, -90.0, 180.0, 90.0), box.coordinatesInApiOrder())
    }

    @Test
    fun bboxRejectsDegenerateInvertedAndNonWgs84Bounds() {
        assertFailsWith<IllegalArgumentException> { BoundingBox(40.0, 50.0, 40.0, 55.0) }
        assertFailsWith<IllegalArgumentException> { BoundingBox(40.0, 50.0, 45.0, 50.0) }
        assertFailsWith<IllegalArgumentException> { BoundingBox(170.0, 50.0, -170.0, 55.0) }
        assertFailsWith<IllegalArgumentException> { BoundingBox(40.0, 55.0, 45.0, 50.0) }
        assertFailsWith<IllegalArgumentException> { BoundingBox(-181.0, 50.0, 45.0, 55.0) }
        assertFailsWith<IllegalArgumentException> { BoundingBox(40.0, -91.0, 45.0, 55.0) }
        assertFailsWith<IllegalArgumentException> { BoundingBox(40.0, 50.0, 181.0, 55.0) }
        assertFailsWith<IllegalArgumentException> { BoundingBox(40.0, 50.0, 45.0, 91.0) }
        assertFailsWith<IllegalArgumentException> { BoundingBox(40.0, 50.0, Double.NaN, 55.0) }
    }

    @Test
    fun positionRejectsOutOfRangeAndNonFiniteCoordinates() {
        assertFailsWith<IllegalArgumentException> { Position(latitude = 100.0, longitude = 60.0) }
        assertFailsWith<IllegalArgumentException> { Position(latitude = 60.0, longitude = 200.0) }
        assertFailsWith<IllegalArgumentException> { Position(latitude = Double.NaN, longitude = 60.0) }
        assertFailsWith<IllegalArgumentException> { Position(latitude = 60.0, longitude = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun pointIdentitySurvivesCoordinateChangeAndDistinguishesColocatedPoints() {
        val pointId = "a6e52021-23f7-42da-92ef-888561f781e8"
        val colocatedId = "003cf4f0-4084-4a85-92d3-079b5c129507"
        val position = Position(latitude = 55.75, longitude = 37.61)
        val first = MapMarker(pointId = pointId, position = position)
        val moved = first.copy(position = Position(latitude = 55.76, longitude = 37.61))
        val colocated = MapMarker(pointId = colocatedId, position = position)

        assertEquals(pointId, moved.pointId)
        assertNotEquals(first.pointId, colocated.pointId)
        assertFailsWith<IllegalArgumentException> { MapMarker(pointId = " ", position = position) }
    }
}
