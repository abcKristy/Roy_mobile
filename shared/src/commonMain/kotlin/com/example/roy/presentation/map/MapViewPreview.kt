package com.example.roy.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.Position
import com.example.roy.presentation.theme.RoyTheme

@Preview
@Composable
private fun MapViewLightPreview() = MapViewPreview(dark = false)

@Preview
@Composable
private fun MapViewDarkPreview() = MapViewPreview(dark = true)

@Composable
private fun MapViewPreview(dark: Boolean) {
    RoyTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalMapSurface provides PreviewMapSurface) {
            MapView(MapViewState(viewport = ViewportContent.Ready, renderStatus = MapRenderStatus.Ready)) {}
        }
    }
}

private object PreviewMapSurface : MapSurface {
    @Composable
    override fun Content(
        state: MapSurfaceState,
        onViewportChanged: (BoundingBox) -> Unit,
        onPinSelected: (String) -> Unit,
        onMapTapped: (Position) -> Unit,
        onRenderStatusChanged: (MapRenderStatus) -> Unit,
        modifier: Modifier,
    ) {
        Box(modifier.background(RoyTheme.colors.background), contentAlignment = Alignment.Center) {
            Row(
                Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MapPointPin("football", isSelected = false, contentDescription = "Футбол", onClick = {})
                MapMultiTopicPin(3, isSelected = true, contentDescription = "Три темы", onClick = {})
                MapDeviceMarker()
            }
        }
    }
}
