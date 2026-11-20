package com.example.roy.presentation.points.detail


internal sealed class PointDetailAction {
    data object Back : PointDetailAction()
    data class RequireJoin(val pointId: String, val topicId: String, val chatId: String) : PointDetailAction()
    data class RequireAdd(val pointId: String) : PointDetailAction()
    data class OpenChat(val pointId: String, val topicId: String, val chatId: String) : PointDetailAction()
    data class PointChanged(val pointId: String, val isGone: Boolean) : PointDetailAction()
}
