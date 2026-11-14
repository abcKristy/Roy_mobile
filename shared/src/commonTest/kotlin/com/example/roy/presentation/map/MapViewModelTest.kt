package com.example.roy.presentation.map

import com.example.roy.domain.location.DeviceLocation
import com.example.roy.domain.location.DeviceLocationResult
import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.map.LoadViewport
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.PointPage
import com.example.roy.domain.map.PointRepository
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.Position
import com.example.roy.domain.map.ViewportQuery
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @Test
    fun viewportIgnoresLateSuccessFromCancelledSource() = mapTest {
        val source = ControlledPoints()
        val model = model(source)
        model.handleEvent(MapViewEvent.ViewportChanged(boundsA))
        runCurrent()
        model.handleEvent(MapViewEvent.ViewportChanged(boundsB))
        runCurrent()

        source.page(boundsB).reply.resume(page(pointB))
        runCurrent()
        source.page(boundsA).reply.resume(page(pointA))
        runCurrent()

        assertEquals(ViewportQuery(boundsB), model.viewState.value.query)
        assertEquals(listOf(pointB), model.viewState.value.points)
        assertEquals(ViewportContent.Ready, model.viewState.value.viewport)
    }

    @Test
    fun secondPageFailureKeepsLastCompleteViewportAndRetryUsesCurrentFilter() = mapTest {
        val source = ControlledPoints()
        val model = model(source)
        model.handleEvent(MapViewEvent.ViewportChanged(boundsA))
        runCurrent()
        source.page(boundsA).reply.resume(page(pointA))
        runCurrent()

        val filter = PointFilter(topicKey = "football")
        model.handleEvent(MapViewEvent.FilterChanged(filter))
        runCurrent()
        val first = source.pages.last()
        first.reply.resume(page(pointB, "second"))
        runCurrent()
        val second = source.pages.last()
        assertEquals(first.query, second.query)
        assertEquals("second", second.cursor)
        second.reply.resume(PointResult.Failure(PointError.Network))
        runCurrent()

        assertEquals(listOf(pointA), model.viewState.value.points)
        assertEquals(ViewportContent.Failed(PointError.Network), model.viewState.value.viewport)
        model.handleEvent(MapViewEvent.RetryViewport)
        runCurrent()
        val retry = source.pages.last()
        assertEquals(ViewportQuery(boundsA, filter), retry.query)
        assertNull(retry.cursor)
        retry.reply.resume(page(pointB))
        runCurrent()
        assertEquals(listOf(pointB), model.viewState.value.points)
        assertEquals(ViewportContent.Ready, model.viewState.value.viewport)
    }

    @Test
    fun latestSelectionIgnoresOldSuccessAndGoneFailure() = mapTest {
        for (oldResult in listOf(
            PointResult.Success(pointA),
            PointResult.Failure(PointError.ResourceGone),
        )) {
            val source = ControlledPoints()
            val model = model(source)
            val actions = mutableListOf<MapViewAction>()
            val collector = backgroundScope.launch { model.viewAction.collect { actions += it } }

            model.handleEvent(MapViewEvent.PinSelected(pointA.id))
            runCurrent()
            model.handleEvent(MapViewEvent.PinSelected(pointB.id))
            runCurrent()
            source.detail(pointB.id).resume(PointResult.Success(pointB))
            runCurrent()
            source.detail(pointA.id).resume(oldResult)
            runCurrent()

            assertEquals(pointB.id, model.viewState.value.selectedPointId)
            assertEquals(PointPreview.Ready(pointB), model.viewState.value.preview)
            assertTrue(actions.isEmpty())
            collector.cancel()
        }
    }

    @Test
    fun closingPreviewIgnoresLateDetailAndCurrentGoneClearsSelection() = mapTest {
        val source = ControlledPoints()
        val model = model(source)
        val actions = mutableListOf<MapViewAction>()
        backgroundScope.launch { model.viewAction.collect { actions += it } }
        model.handleEvent(MapViewEvent.ViewportChanged(boundsA))
        runCurrent()
        source.page(boundsA).reply.resume(page(pointA))
        runCurrent()
        model.handleEvent(MapViewEvent.PinSelected(pointA.id))
        runCurrent()
        val closed = source.detail(pointA.id)
        model.handleEvent(MapViewEvent.PreviewClosed)
        closed.resume(PointResult.Success(pointA))
        runCurrent()
        assertNull(model.viewState.value.selectedPointId)
        assertNull(model.viewState.value.preview)

        model.handleEvent(MapViewEvent.PinSelected(pointA.id))
        runCurrent()
        source.detail(pointA.id).resume(PointResult.Failure(PointError.ResourceGone))
        runCurrent()
        assertNull(model.viewState.value.selectedPointId)
        assertNull(model.viewState.value.preview)
        assertTrue(model.viewState.value.points.isEmpty())
        assertEquals(listOf<MapViewAction>(MapViewAction.PointGone), actions)
    }

    @Test
    fun detailsUsesCurrentReadyPointAndDoesNotReplayAfterCollectorRestarts() = mapTest {
        val source = ControlledPoints()
        val model = model(source)
        val actions = mutableListOf<MapViewAction>()
        val collector = backgroundScope.launch { model.viewAction.collect { actions += it } }

        model.handleEvent(MapViewEvent.PinSelected(pointA.id))
        runCurrent()
        source.detail(pointA.id).resume(PointResult.Success(pointA))
        runCurrent()
        model.handleEvent(MapViewEvent.PinSelected(pointB.id))
        model.handleEvent(MapViewEvent.DetailsRequested)
        runCurrent()
        assertTrue(actions.isEmpty())
        source.detail(pointB.id).resume(PointResult.Success(pointB))
        runCurrent()

        model.handleEvent(MapViewEvent.DetailsRequested)
        model.handleEvent(MapViewEvent.DetailsRequested)
        runCurrent()
        collector.cancel()
        runCurrent()
        backgroundScope.launch { model.viewAction.collect { actions += it } }
        runCurrent()

        assertEquals(listOf<MapViewAction>(MapViewAction.OpenDetails(pointB.id)), actions)
    }

    @Test
    fun pendingLocationRequestsAreCoalescedAndCameraCommandCanBeConsumedOnce() = mapTest {
        val result = CompletableDeferred<DeviceLocationResult>()
        var locationCalls = 0
        val location = DeviceLocation {
            locationCalls++
            result.await()
        }
        val model = model(ControlledPoints(), location)
        model.handleEvent(MapViewEvent.LocateRequested)
        model.handleEvent(MapViewEvent.LocateRequested)
        runCurrent()
        assertTrue(model.viewState.value.isLocating)
        assertEquals(1, locationCalls)

        result.complete(DeviceLocationResult.Located(pointA.position))
        runCurrent()
        val command = assertNotNull(model.viewState.value.cameraCommand)
        assertEquals(pointA.position, model.viewState.value.devicePosition)
        assertFalse(model.viewState.value.isLocating)
        model.handleEvent(MapViewEvent.CameraApplied(command.requestId))
        model.handleEvent(MapViewEvent.CameraApplied(command.requestId))
        assertNull(model.viewState.value.cameraCommand)
        assertEquals(command.camera, model.viewState.value.camera)
    }

    @Test
    fun returningWithLocationDisabledClearsOldMarkerWithoutLocatingAgain() = mapTest {
        var accessProblem: DeviceLocationResult? = null
        var locationCalls = 0
        val location = object : DeviceLocation {
            override fun accessProblem() = accessProblem
            override suspend fun locate(): DeviceLocationResult {
                locationCalls++
                return DeviceLocationResult.Located(pointA.position)
            }
        }
        val model = model(ControlledPoints(), location)
        model.handleEvent(MapViewEvent.LocateRequested)
        runCurrent()
        val command = assertNotNull(model.viewState.value.cameraCommand)
        model.handleEvent(MapViewEvent.CameraApplied(command.requestId))

        accessProblem = DeviceLocationResult.Disabled
        model.handleEvent(MapViewEvent.ScreenResumed)
        assertNull(model.viewState.value.devicePosition)
        assertEquals(DeviceLocationResult.Disabled, model.viewState.value.locationProblem)
        assertEquals(command.camera, model.viewState.value.camera)
        assertNull(model.viewState.value.cameraCommand)
        assertEquals(1, locationCalls)
    }

    private fun mapTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun model(
        source: ControlledPoints,
        location: DeviceLocation = DeviceLocation { DeviceLocationResult.Unavailable },
    ) = MapViewModel(LoadViewport(source), LoadPoint(source), location)

    private class ControlledPoints : PointRepository {
        val pages = mutableListOf<PageRequest>()
        private val details = mutableMapOf<String, Continuation<PointResult<Point>>>()

        // Intentionally resume the old call even after its caller cancels.
        override suspend fun getViewportPage(query: ViewportQuery, cursor: String?): PointResult<PointPage> =
            suspendCoroutine { pages += PageRequest(query, cursor, it) }

        override suspend fun getPoint(pointId: String): PointResult<Point> =
            suspendCoroutine { details[pointId] = it }

        fun page(bounds: BoundingBox) = pages.single { it.query.bbox == bounds }

        fun detail(pointId: String) = details.getValue(pointId)
    }

    private data class PageRequest(
        val query: ViewportQuery,
        val cursor: String?,
        val reply: Continuation<PointResult<PointPage>>,
    )

    private companion object {
        val boundsA = BoundingBox(west = 37.59, south = 55.72, east = 37.63, north = 55.76)
        val boundsB = BoundingBox(west = 37.63, south = 55.76, east = 37.67, north = 55.80)
        val pointA = point("a", Position(latitude = 55.74, longitude = 37.61))
        val pointB = point("b", Position(latitude = 55.78, longitude = 37.65))

        fun page(point: Point, next: String? = null) = PointResult.Success(PointPage(listOf(point), next))

        fun point(id: String, position: Position): Point {
            val time = Instant.parse("2026-11-14T12:00:00Z")
            return Point(id, position, "Test address", "manual", 1, emptyList(), time, time)
        }
    }
}
