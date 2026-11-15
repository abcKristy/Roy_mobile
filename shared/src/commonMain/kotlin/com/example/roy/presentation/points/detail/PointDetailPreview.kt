package com.example.roy.presentation.points.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointTopic
import com.example.roy.domain.map.Position
import com.example.roy.presentation.theme.RoyTheme
import kotlin.time.Instant

@Preview
@Composable
private fun PointDetailLightPreview() = DetailPreview(isDark = false)

@Preview
@Composable
private fun PointDetailDarkPreview() = DetailPreview(isDark = true)

@Composable
private fun DetailPreview(isDark: Boolean) {
    val created = Instant.parse("2026-11-15T00:00:00Z")
    val state = PointDetailState(
        point = Point(
            id = "point",
            position = Position(55.76, 37.62),
            address = "Ленинский проспект, 51",
            addressSource = "manual",
            peopleCount = 11,
            topics = listOf(
                PointTopic("first", "other", "Шахматы", 8, "first-chat"),
                PointTopic("second", "football", "Футбол", 5, "second-chat"),
            ),
            createdAt = created,
            updatedAt = created,
        ),
        selectedTopicId = "first",
        memberChatIds = setOf("first-chat"),
        isAuthorized = true,
        isMembershipKnown = true,
        isLoading = false,
    )
    RoyTheme(darkTheme = isDark) {
        PointDetailView(state, pointDetailStrings(state)) {}
    }
}
