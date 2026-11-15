package com.example.roy.presentation.filters

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.points.TopicPreset
import com.example.roy.presentation.theme.RoyTheme

@Preview
@Composable
private fun MapFilterLightPreview() = FilterPreview(dark = false)

@Preview
@Composable
private fun MapFilterDarkPreview() = FilterPreview(dark = true)

@Composable
private fun FilterPreview(dark: Boolean) {
    RoyTheme(darkTheme = dark) {
        MapFilterView(
            MapFilterViewState(
                topicKey = "football", customLabel = "", peopleMin = "0", peopleMax = "50",
                presets = listOf(TopicPreset("football", "Футбол")), loadingPresets = false,
            ),
            eventHandler = {},
        )
    }
}
