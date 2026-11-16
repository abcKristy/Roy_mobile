package com.example.roy.presentation.chat

internal sealed class TopicChatViewAction {
    data object CloseScreen : TopicChatViewAction()
    data class ShowLeaveError(val issue: TopicChatIssue) : TopicChatViewAction()
    data class Left(val result: LeaveTopicResult) : TopicChatViewAction()
    data class AccessLost(val result: LeaveTopicResult) : TopicChatViewAction()
}

internal data class LeaveTopicResult(val pointId: String, val chatId: String, val isPointGone: Boolean)
