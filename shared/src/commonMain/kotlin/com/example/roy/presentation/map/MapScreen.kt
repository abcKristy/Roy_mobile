package com.example.roy.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.map.Position
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.components.overlays.RoySnackbarMessage
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.*

internal val LocalMapSurface = staticCompositionLocalOf<MapSurface> { error("Map renderer is required") }

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun MapScreen(
    viewModel: MapViewModel,
    snackbar: RoySnackbarHostState,
    nextMessageId: () -> Long,
    onOpenDetails: (String) -> Unit,
    onCreatePoint: (Position) -> Boolean,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val gone = stringResource(Res.string.map_point_gone)
    val eventHandler: (MapViewEvent) -> Unit = remember(viewModel) { viewModel::handleEvent }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { eventHandler(MapViewEvent.ScreenResumed) }
    DisposableEffect(viewModel) {
        onDispose { eventHandler(MapViewEvent.ScreenLeft) }
    }
    BackHandler(enabled = state.selectedPointId != null) { eventHandler(MapViewEvent.PreviewClosed) }
    val dark = RoyTheme.isDark
    val surface = remember(dark) { MapLibreSurface(MapStyleConfig.url(dark)) }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            is MapViewAction.OpenDetails -> onOpenDetails(action.pointId)
            is MapViewAction.OpenCreate -> {
                if (!onCreatePoint(action.position)) eventHandler(MapViewEvent.CreateNavigationDeclined)
            }
            MapViewAction.PointGone -> snackbar.show(RoySnackbarMessage(nextMessageId(), gone))
        }
    }
    CompositionLocalProvider(LocalMapSurface provides surface) {
        MapView(state, eventHandler)
    }
}

@Composable
internal fun MapView(viewState: MapViewState, eventHandler: (MapViewEvent) -> Unit) {
    val surface = LocalMapSurface.current
    val spacing = RoyTheme.dimensions.spacing
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxSize().background(RoyTheme.colors.background)) {
        key(surface, viewState.surfaceRevision) {
            surface.Content(
                state = MapSurfaceState(
                    initialCamera = viewState.camera,
                    cameraCommand = viewState.cameraCommand,
                    devicePosition = viewState.devicePosition,
                    selectedPointId = viewState.selectedPointId,
                    markers = viewState.points.map {
                        MapMarker(it.id, it.position, it.address, it.topics.map { topic -> topic.presetKey })
                    },
                    onCameraApplied = { eventHandler(MapViewEvent.CameraApplied(it)) },
                    onCameraSettled = { eventHandler(MapViewEvent.CameraSettled(it)) },
                ),
                onViewportChanged = { eventHandler(MapViewEvent.ViewportChanged(it)) },
                onPinSelected = { eventHandler(MapViewEvent.PinSelected(it)) },
                onMapTapped = { eventHandler(MapViewEvent.MapTapped(it)) },
                onRenderStatusChanged = { eventHandler(MapViewEvent.RenderingChanged(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) {
                TertiaryButton(
                    label = stringResource(Res.string.protected_create),
                    onClick = { eventHandler(MapViewEvent.CreateRequested) },
                )
                MapLocationButton(
                    isLocating = viewState.isLocating,
                    isEnabled = !viewState.isLocating,
                    onClick = { eventHandler(MapViewEvent.LocateRequested) },
                )
            }
            MapFeedback(viewState, eventHandler)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                when (val preview = viewState.preview) {
                    is PointPreview.Ready -> PointPreviewCard(
                        point = preview.point,
                        peopleLabel = pluralStringResource(Res.plurals.map_people, preview.point.peopleCount, preview.point.peopleCount),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                            PointDetailsButton(isEnabled = true) { eventHandler(MapViewEvent.DetailsRequested) }
                            TertiaryButton(stringResource(Res.string.map_close), { eventHandler(MapViewEvent.PreviewClosed) })
                        }
                    }
                    PointPreview.Loading -> RoyCard {
                        LoadingState(LoadingMode.Inline, stringResource(Res.string.map_detail_loading))
                        PointDetailsButton(isEnabled = false) {}
                        TertiaryButton(stringResource(Res.string.map_close), { eventHandler(MapViewEvent.PreviewClosed) })
                    }
                    is PointPreview.Failed -> RoyCard {
                        MapStatus(pointErrorLabel(preview.error), MapViewEvent.RetryPreview, eventHandler)
                        TertiaryButton(stringResource(Res.string.map_close), { eventHandler(MapViewEvent.PreviewClosed) })
                    }
                    null -> Unit
                }
            }
            MapAttribution(onOpenLink = { uri.openUri(it) })
        }
    }
}

internal object MapStyleConfig {
    fun url(dark: Boolean): String = if (dark) {
        "https://tiles.openfreemap.org/styles/dark"
    } else {
        "https://tiles.openfreemap.org/styles/liberty"
    }
}
