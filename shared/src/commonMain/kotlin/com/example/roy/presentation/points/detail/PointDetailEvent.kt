package com.example.roy.presentation.points.detail


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
