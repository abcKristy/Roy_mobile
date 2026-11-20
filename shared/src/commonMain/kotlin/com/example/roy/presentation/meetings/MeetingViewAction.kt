package com.example.roy.presentation.meetings


internal sealed class MeetingViewAction {
    data object RefreshChatHistory : MeetingViewAction()
}
