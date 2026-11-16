package com.example.roy.presentation.chat

internal sealed class TopicChatViewEvent {
    data object OnBack : TopicChatViewEvent()
    data object OnResume : TopicChatViewEvent()
    data object OnPause : TopicChatViewEvent()
    data object OnRetry : TopicChatViewEvent()
    data object OnLoadOlder : TopicChatViewEvent()
    data object OnOpenSearch : TopicChatViewEvent()
    data object OnCloseSearch : TopicChatViewEvent()
    data class OnSearchQueryChanged(val query: String) : TopicChatViewEvent()
    data object OnSearchSubmitted : TopicChatViewEvent()
    data object OnSearchMore : TopicChatViewEvent()
    data class OnVisibleMessage(val seq: Long) : TopicChatViewEvent()
    data class OnMeetingOverlayChanged(val isOpen: Boolean) : TopicChatViewEvent()
    data object OnLeaveRequested : TopicChatViewEvent()
    data object OnLeaveConfirmed : TopicChatViewEvent()
    data object OnLeaveDismissed : TopicChatViewEvent()
    data class OnComposerEdited(val text: String) : TopicChatViewEvent()
    data object OnSend : TopicChatViewEvent()
    data class OnReportRequested(val messageId: String) : TopicChatViewEvent()
    data object OnReportDismissed : TopicChatViewEvent()
}
