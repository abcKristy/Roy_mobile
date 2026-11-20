package com.example.roy.presentation.chat


internal sealed class ChatReportEvent {
    data class OnEdit(val reason: String) : ChatReportEvent()
    data object OnSubmit : ChatReportEvent()
    data object OnClose : ChatReportEvent()
}
