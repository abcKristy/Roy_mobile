package com.example.roy.presentation.points.detail

import androidx.compose.runtime.Immutable
import com.example.roy.domain.map.Point
import com.example.roy.domain.points.TopicPreset

@Immutable
internal data class PointDetailState(
    val point: Point? = null,
    val selectedTopicId: String? = null,
    val memberChatIds: Set<String> = emptySet(),
    val isAuthorized: Boolean = false,
    val isMembershipKnown: Boolean = false,
    val isLoading: Boolean = true,
    val isGone: Boolean = false,
    val operation: PointDetailOperation? = null,
    val isAddSheetOpen: Boolean = false,
    val isLeaveDialogOpen: Boolean = false,
    val presets: List<TopicPreset> = emptyList(),
    val isLoadingPresets: Boolean = false,
    val presetKey: String? = null,
    val customLabel: String = "",
    val error: PointDetailError? = null,
    val sheetError: PointDetailError? = null,
) {
    val selectedTopic get() = point?.topics?.find { it.id == selectedTopicId }
    val isBusy get() = isLoading || operation != null
}
