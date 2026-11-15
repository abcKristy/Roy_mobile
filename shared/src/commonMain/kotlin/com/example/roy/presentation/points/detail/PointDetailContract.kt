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

internal enum class PointDetailOperation { Add, Join, Leave }

internal enum class PointDetailError { Network, Unavailable, Session, Duplicate, InvalidTopic }

internal sealed class PointDetailEvent {
    data object OnBack : PointDetailEvent()
    data object OnRetry : PointDetailEvent()
    data class OnTopicSelected(val topicId: String) : PointDetailEvent()
    data object OnJoinRequested : PointDetailEvent()
    data object OnAddRequested : PointDetailEvent()
    data object OnPresetsRetry : PointDetailEvent()
    data class OnPresetSelected(val presetKey: String) : PointDetailEvent()
    data class OnCustomLabelChanged(val label: String) : PointDetailEvent()
    data object OnAddConfirmed : PointDetailEvent()
    data object OnLeaveRequested : PointDetailEvent()
    data object OnLeaveConfirmed : PointDetailEvent()
    data object OnDialogDismissed : PointDetailEvent()
}

internal sealed class PointDetailAction {
    data object Back : PointDetailAction()
    data class RequireJoin(val pointId: String, val topicId: String, val chatId: String) : PointDetailAction()
    data class RequireAdd(val pointId: String) : PointDetailAction()
    data class OpenChat(val pointId: String, val topicId: String, val chatId: String) : PointDetailAction()
    data class PointChanged(val pointId: String, val isGone: Boolean) : PointDetailAction()
}
