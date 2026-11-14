package com.example.roy.presentation.map

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.location.DeviceLocation
import com.example.roy.domain.location.DeviceLocationResult
import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.LoadViewport
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.Position
import com.example.roy.domain.map.ViewportQuery
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

internal val MoscowCamera = MapCamera(Position(latitude = 55.751244, longitude = 37.618423), zoom = 12.0)

internal data class MapViewState(
    val camera: MapCamera = MoscowCamera,
    val cameraCommand: MapCameraCommand? = null,
    val devicePosition: Position? = null,
    val isLocating: Boolean = false,
    val locationProblem: DeviceLocationResult? = null,
    val query: ViewportQuery? = null,
    val viewport: ViewportContent = ViewportContent.Loading,
    val points: List<Point> = emptyList(),
    val selectedPointId: String? = null,
    val preview: PointPreview? = null,
    val renderStatus: MapRenderStatus = MapRenderStatus.Loading,
    val surfaceRevision: Int = 0,
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
    data class FilterChanged(val filter: PointFilter) : MapViewEvent()
    data class PinSelected(val pointId: String) : MapViewEvent()
    data class MapTapped(val position: Position) : MapViewEvent()
    data class RenderingChanged(val status: MapRenderStatus) : MapViewEvent()
    data class CameraApplied(val requestId: Long) : MapViewEvent()
    data class CameraSettled(val camera: MapCamera) : MapViewEvent()
    data object LocateRequested : MapViewEvent()
    data object ScreenLeft : MapViewEvent()
    data object PreviewClosed : MapViewEvent()
    data object DetailsRequested : MapViewEvent()
    data object RetryViewport : MapViewEvent()
    data object RetryPreview : MapViewEvent()
    data object RetryBasemap : MapViewEvent()
}

internal sealed class MapViewAction {
    data class OpenDetails(val pointId: String) : MapViewAction()
    data class PositionTapped(val position: Position) : MapViewAction()
    data object PointGone : MapViewAction()
}

internal class MapViewModel(
    private val loadViewport: LoadViewport,
    private val loadPoint: LoadPoint,
    private val location: DeviceLocation,
) : BaseViewModel<MapViewState, MapViewEvent, MapViewAction>(MapViewState()) {
    private var filter = PointFilter()
    private var viewportGeneration = 0L
    private var detailGeneration = 0L
    private var cameraGeneration = 0L
    private var viewportJob: Job? = null
    private var locationJob: Job? = null
    private var detailJob: Job? = null
    private var navigationJob: Job? = null

    override fun handleEvent(event: MapViewEvent) {
        when (event) {
            is MapViewEvent.ViewportChanged -> viewportChanged(event.bounds)
            is MapViewEvent.FilterChanged -> filterChanged(event.filter)
            is MapViewEvent.PinSelected -> pinSelected(event.pointId)
            is MapViewEvent.MapTapped -> mapTapped(event.position)
            is MapViewEvent.RenderingChanged -> updateState { it.copy(renderStatus = event.status) }
            is MapViewEvent.CameraApplied -> cameraApplied(event.requestId)
            is MapViewEvent.CameraSettled -> updateState { it.copy(camera = event.camera) }
            MapViewEvent.LocateRequested -> locateRequested()
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
        val query = ViewportQuery(bounds, filter)
        if (query != viewState.value.query) requestViewport(query)
    }

    private fun filterChanged(newFilter: PointFilter) {
        if (filter == newFilter) return
        filter = newFilter
        viewState.value.query?.let { requestViewport(it.copy(filter = filter)) }
    }

    private fun requestViewport(query: ViewportQuery) {
        val generation = ++viewportGeneration
        viewportJob?.cancel()
        updateState { it.copy(query = query, viewport = ViewportContent.Loading) }
        viewportJob = viewModelScope.launch {
            val result = loadViewport(query)
            if (generation != viewportGeneration) return@launch
            updateState { state ->
                when (result) {
                    is PointResult.Success -> state.copy(points = result.value, viewport = ViewportContent.Ready)
                    is PointResult.Failure -> state.copy(viewport = ViewportContent.Failed(result.error))
                }
            }
        }
    }

    private fun pinSelected(pointId: String) {
        if (viewState.value.selectedPointId != pointId) requestPoint(pointId)
    }

    private fun requestPoint(pointId: String) {
        val generation = ++detailGeneration
        detailJob?.cancel()
        updateState { it.copy(selectedPointId = pointId, preview = PointPreview.Loading) }
        detailJob = viewModelScope.launch {
            val result = loadPoint(pointId)
            if (generation != detailGeneration || viewState.value.selectedPointId != pointId) return@launch
            when (result) {
                is PointResult.Success -> updateState { it.copy(preview = PointPreview.Ready(result.value)) }
                is PointResult.Failure -> {
                    if (result.error == PointError.ResourceGone) {
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
        viewModelScope.launch { sendAction(MapViewAction.PositionTapped(position)) }
    }

    private fun detailsRequested() {
        val state = viewState.value
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
