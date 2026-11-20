package com.example.roy.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.map.Position
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.components.overlays.RoySnackbarMessage
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_link_open_error
import roy.shared.generated.resources.map_point_gone
import roy.shared.generated.resources.map_single_pin_label

internal val LocalMapSurface = staticCompositionLocalOf<MapSurface> { error("Map renderer is required") }

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun MapScreen(
    viewModel: MapViewModel,
    snackbar: RoySnackbarHostState,
    nextMessageId: () -> Long,
    onOpenDetails: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onCreatePoint: (Position) -> Boolean,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val linkFailure = stringResource(Res.string.map_link_open_error)
    val gone = stringResource(Res.string.map_point_gone)
    val eventHandler: (MapViewEvent) -> Unit = remember(viewModel) { viewModel::handleEvent }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { eventHandler(MapViewEvent.OnScreenResumed) }
    DisposableEffect(viewModel) {
        onDispose { eventHandler(MapViewEvent.OnScreenLeft) }
    }
    BackHandler(enabled = viewState.selectedPointId != null) { eventHandler(MapViewEvent.OnPreviewClosed) }
    val dark = RoyTheme.isDark
    val isOffline = !viewState.isOnline || viewState.isUsingCache
    val surface = remember(dark, isOffline) { MapLibreSurface(if (isOffline) null else MapStyleConfig.url(dark)) }
    val markers = viewState.points.map { point ->
        val topic = point.topics.singleOrNull()
        val label = if (topic == null) {
            point.address
        } else {
            stringResource(Res.string.map_single_pin_label, point.address, topic.label)
        }
        MapMarker(
            pointId = point.id,
            position = point.position,
            label = label,
            topicKeys = point.topics.map { it.presetKey },
        )
    }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            is MapViewAction.OpenAttribution -> {
                try { uri.openUri(action.url) }
                catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { snackbar.show(RoySnackbarMessage(nextMessageId(), linkFailure)) }
            }
            MapViewAction.OpenFilters -> onOpenFilters()
            is MapViewAction.OpenDetails -> onOpenDetails(action.pointId)
            is MapViewAction.OpenCreate -> {
                if (!onCreatePoint(action.position)) eventHandler(MapViewEvent.OnCreateNavigationDeclined)
            }
            MapViewAction.PointGone -> snackbar.show(RoySnackbarMessage(nextMessageId(), gone))
        }
    }
    CompositionLocalProvider(LocalMapSurface provides surface) {
        MapView(viewState, markers, eventHandler)
    }
}

internal object MapStyleConfig {
    fun url(dark: Boolean): String = if (dark) {
        "https://tiles.openfreemap.org/styles/dark"
    } else {
        "https://tiles.openfreemap.org/styles/liberty"
    }
}
