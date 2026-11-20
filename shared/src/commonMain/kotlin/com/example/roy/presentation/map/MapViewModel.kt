package com.example.roy.presentation.map

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.offline.PinCacheStore
import com.example.roy.domain.location.DeviceLocation
import com.example.roy.domain.location.DeviceLocationResult
import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.LoadViewport
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.PointFilterStore
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.Position
import com.example.roy.domain.map.ViewportQuery
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

internal val MoscowCamera = MapCamera(Position(latitude = 55.751244, longitude = 37.618423), zoom = 12.0)

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

internal sealed interface ViewportContent {
    data object Loading : ViewportContent
    data object Ready : ViewportContent
    data class Failed(val error: PointError) : ViewportContent
}

internal sealed interface PointPreview {
    data object Loading : PointPreview
    data class Ready(val point: Point) : PointPreview
    data class Failed(val error: PointError) : PointPreview
}

internal sealed class MapViewEvent {
    data class ViewportChanged(val bounds: BoundingBox) : MapViewEvent()
    data object OpenFilters : MapViewEvent()
    data class FilterChanged(val filter: PointFilter) : MapViewEvent()
    data class PointChanged(val pointId: String, val isGone: Boolean) : MapViewEvent()
    data class PinSelected(val pointId: String) : MapViewEvent()
    data class MapTapped(val position: Position) : MapViewEvent()
    data object CreateRequested : MapViewEvent()
    data object CreateNavigationDeclined : MapViewEvent()
    data class RenderingChanged(val status: MapRenderStatus) : MapViewEvent()
    data class CameraApplied(val requestId: Long) : MapViewEvent()
    data class CameraSettled(val camera: MapCamera) : MapViewEvent()
    data object LocateRequested : MapViewEvent()
    data object ScreenResumed : MapViewEvent()
    data object ScreenLeft : MapViewEvent()
    data object PreviewClosed : MapViewEvent()
    data object DetailsRequested : MapViewEvent()
    data object RetryViewport : MapViewEvent()
    data object RetryPreview : MapViewEvent()
    data object RetryBasemap : MapViewEvent()
}

internal sealed class MapViewAction {
    data object OpenFilters : MapViewAction()
    data class OpenDetails(val pointId: String) : MapViewAction()
    data class OpenCreate(val position: Position) : MapViewAction()
    data object PointGone : MapViewAction()
}

internal class MapViewModel(
    private val loadViewport: LoadViewport,
    private val loadPoint: LoadPoint,
    private val location: DeviceLocation,
    private val filterStore: PointFilterStore? = null,
    private val offlineCache: PinCacheStore? = null,
    connectivity: Flow<Boolean> = flowOf(true),
    private val onSettledPosition: (Position) -> Unit = {},
) : BaseViewModel<MapViewState, MapViewEvent, MapViewAction>(
    MapViewState(appliedFilter = filterStore?.read() ?: PointFilter()),
) {
    private var viewportGeneration = 0L
    private var detailGeneration = 0L
    private var cameraGeneration = 0L
    private var viewportJob: Job? = null
    private var locationJob: Job? = null
    private var detailJob: Job? = null
    private var navigationJob: Job? = null

    private var createNavigationRequested = false

    init {
        viewModelScope.launch {
            connectivity.collect { online ->
                if (online == viewState.value.isOnline) return@collect
                updateState { it.copy(isOnline = online, createPosition = null) }
                val state = viewState.value
                state.selectedPointId?.let(::requestPoint)
                val last = if (!online) offlineCache?.lastPins(state.appliedFilter) else null
                val query = if (last != null && state.points.isEmpty()) {
                    val camera = MapCamera(last.center, last.zoom)
                    updateState { it.copy(camera = camera, cameraCommand = MapCameraCommand(++cameraGeneration, camera)) }
                    last.query
                } else state.query
                query?.let(::requestViewport)
            }
        }
    }

    override fun handleEvent(event: MapViewEvent) {
        when (event) {
            is MapViewEvent.ViewportChanged -> viewportChanged(event.bounds)
            MapViewEvent.OpenFilters -> viewModelScope.launch { sendAction(MapViewAction.OpenFilters) }
            is MapViewEvent.FilterChanged -> filterChanged(event.filter)
            is MapViewEvent.PointChanged -> pointChanged(event.pointId, event.isGone)
            is MapViewEvent.PinSelected -> {
                updateState { it.copy(createPosition = null) }
                pinSelected(event.pointId)
            }
            is MapViewEvent.MapTapped -> mapTapped(event.position)
            MapViewEvent.CreateRequested -> createRequested()
            MapViewEvent.CreateNavigationDeclined -> createNavigationRequested = false
            is MapViewEvent.RenderingChanged -> updateState { it.copy(renderStatus = event.status) }
            is MapViewEvent.CameraApplied -> cameraApplied(event.requestId)
            is MapViewEvent.CameraSettled -> if (viewState.value.camera != event.camera) {
                updateState { it.copy(camera = event.camera, createPosition = null) }
                if (viewState.value.isOnline) onSettledPosition(event.camera.center)
            }
            MapViewEvent.LocateRequested -> locateRequested()
            MapViewEvent.ScreenResumed -> {
                createNavigationRequested = false
                recheckLocationAccess()
            }
            MapViewEvent.ScreenLeft -> screenLeft()
            MapViewEvent.PreviewClosed -> previewClosed()
            MapViewEvent.DetailsRequested -> detailsRequested()
            MapViewEvent.RetryViewport -> viewState.value.query?.let(::requestViewport)
            MapViewEvent.RetryPreview -> viewState.value.selectedPointId?.let(::requestPoint)
            MapViewEvent.RetryBasemap -> updateState {
                it.copy(renderStatus = MapRenderStatus.Loading, surfaceRevision = it.surfaceRevision + 1)
            }
        }
    }

    private fun viewportChanged(bounds: BoundingBox) {
        val query = ViewportQuery(bounds, viewState.value.appliedFilter)
        if (query != viewState.value.query) requestViewport(query)
    }

    private fun filterChanged(newFilter: PointFilter) {
        if (viewState.value.appliedFilter == newFilter) return
        filterStore?.save(newFilter)
        previewClosed()
        updateState { it.copy(appliedFilter = newFilter, points = emptyList()) }
        viewState.value.query?.let { requestViewport(it.copy(filter = newFilter)) }
    }

    private fun requestViewport(query: ViewportQuery) {
        val generation = ++viewportGeneration
        viewportJob?.cancel()
        updateState { it.copy(query = query, viewport = ViewportContent.Loading) }
        val camera = viewState.value.camera
        viewportJob = viewModelScope.launch {
            val result = if (viewState.value.isOnline) loadViewport(query) else PointResult.Failure(PointError.Network)
            if (generation != viewportGeneration) return@launch
            if (result is PointResult.Success) {
                offlineCache?.savePins(query, result.value, camera.center, camera.zoom)
            }
            val cached = if (result is PointResult.Failure && result.error == PointError.Network) offlineCache?.readPins(query) else null
            coroutineContext.ensureActive()
            if (generation != viewportGeneration) return@launch
            updateState { state ->
                when (result) {
                    is PointResult.Success -> state.copy(points = result.value, viewport = ViewportContent.Ready,
                        isUsingCache = false, fetchedAt = null)
                    is PointResult.Failure -> if (result.error == PointError.Network) {
                        state.copy(points = cached?.points.orEmpty(), isUsingCache = true, fetchedAt = cached?.fetchedAt,
                            viewport = if (cached == null) ViewportContent.Failed(result.error) else ViewportContent.Ready)
                    } else state.copy(viewport = ViewportContent.Failed(result.error), isUsingCache = false, fetchedAt = null)
                }
            }
        }
    }

    private fun pointChanged(pointId: String, isGone: Boolean) {
        if (isGone) {
            viewModelScope.launch { offlineCache?.evictPoint(pointId) }
            if (viewState.value.selectedPointId == pointId) previewClosed()
            updateState { state -> state.copy(points = state.points.filterNot { it.id == pointId }) }
        } else if (viewState.value.selectedPointId == pointId) {
            requestPoint(pointId)
        }
        viewState.value.query?.let(::requestViewport)
    }

    private fun pinSelected(pointId: String) {
        if (viewState.value.selectedPointId != pointId) requestPoint(pointId)
    }

    private fun requestPoint(pointId: String) {
        val generation = ++detailGeneration
        detailJob?.cancel()
        updateState { it.copy(selectedPointId = pointId, preview = PointPreview.Loading) }
        if (!viewState.value.isOnline) {
            val point = viewState.value.points.firstOrNull { it.id == pointId }
            updateState { it.copy(preview = point?.let { value -> PointPreview.Ready(value) } ?: PointPreview.Failed(PointError.Network)) }
            return
        }
        detailJob = viewModelScope.launch {
            val result = loadPoint(pointId)
            if (generation != detailGeneration || viewState.value.selectedPointId != pointId) return@launch
            when (result) {
                is PointResult.Success -> updateState { it.copy(preview = PointPreview.Ready(result.value)) }
                is PointResult.Failure -> {
                    if (result.error == PointError.ResourceGone) {
                        offlineCache?.evictPoint(pointId)
                        coroutineContext.ensureActive()
                        if (generation != detailGeneration) return@launch
                        updateState { state ->
                            state.copy(points = state.points.filterNot { it.id == pointId }, selectedPointId = null, preview = null)
                        }
                        viewState.value.query?.let(::requestViewport)
                        sendAction(MapViewAction.PointGone)
                    } else {
                        updateState { it.copy(preview = PointPreview.Failed(result.error)) }
                    }
                }
            }
        }
    }

    private fun previewClosed() {
        detailGeneration++
        detailJob?.cancel()
        updateState { it.copy(selectedPointId = null, preview = null) }
    }

    private fun mapTapped(position: Position) {
        previewClosed()
        updateState { it.copy(createPosition = position) }
    }

    private fun createRequested() {
        if (createNavigationRequested || !viewState.value.isOnline || viewState.value.isUsingCache) return
        createNavigationRequested = true
        val state = viewState.value
        viewModelScope.launch { sendAction(MapViewAction.OpenCreate(state.createPosition ?: state.camera.center)) }
    }

    private fun detailsRequested() {
        val state = viewState.value
        if (!state.isOnline || state.isUsingCache) return
        val preview = state.preview as? PointPreview.Ready ?: return
        if (preview.point.id != state.selectedPointId || navigationJob?.isActive == true) return
        previewClosed()
        navigationJob = viewModelScope.launch { sendAction(MapViewAction.OpenDetails(preview.point.id)) }
    }

    private fun locateRequested() {
        if (viewState.value.isLocating) return
        updateState { it.copy(isLocating = true, locationProblem = null) }
        locationJob = viewModelScope.launch {
            val result = location.locate()
            coroutineContext.ensureActive()
            when (result) {
                is DeviceLocationResult.Located -> {
                    val camera = MapCamera(result.position, zoom = 15.0)
                    updateState {
                        it.copy(devicePosition = result.position, isLocating = false,
                            cameraCommand = MapCameraCommand(++cameraGeneration, camera))
                    }
                }
                else -> updateState { it.copy(devicePosition = null, isLocating = false, locationProblem = result) }
            }
        }
    }

    private fun recheckLocationAccess() {
        if (viewState.value.devicePosition == null) return
        val problem = location.accessProblem() ?: return
        locationJob?.cancel()
        updateState {
            it.copy(devicePosition = null, isLocating = false, locationProblem = problem, cameraCommand = null)
        }
    }

    private fun screenLeft() {
        locationJob?.cancel()
        previewClosed()
        updateState { it.copy(isLocating = false, cameraCommand = null) }
    }

    private fun cameraApplied(requestId: Long) {
        updateState {
            val command = it.cameraCommand
            if (command?.requestId == requestId) it.copy(camera = command.camera, cameraCommand = null) else it
        }
    }
}
