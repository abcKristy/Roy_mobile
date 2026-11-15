package com.example.roy.presentation.points.create

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.map.Position
import com.example.roy.domain.points.CreatePointDraft
import com.example.roy.domain.points.PointAddressSource
import com.example.roy.domain.points.PointTopicInput
import com.example.roy.domain.points.TopicPreset
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Create point light")
@Composable
internal fun CreatePointLightPreview() = PreviewCreatePoint(darkTheme = false)

@Preview(name = "Create point dark")
@Composable
internal fun CreatePointDarkPreview() = PreviewCreatePoint(darkTheme = true)

@Composable
private fun PreviewCreatePoint(darkTheme: Boolean) {
    RoyTheme(darkTheme = darkTheme) {
        val state = CreatePointViewState(
            draft = CreatePointDraft(
                Position(55.7512, 37.6184),
                PointTopicInput("football"),
                "Площадь Революции, 2",
                PointAddressSource.Manual,
            ),
            presets = listOf(TopicPreset("football", "Футбол"), TopicPreset("volleyball", "Волейбол")),
            isLoadingPresets = false,
            isLoadingContext = false,
            suggestionStatus = AddressSuggestionStatus.None,
            canPublish = true,
        )
        CreatePointView(state, resolveCreatePointText(state)) {}
    }
}
