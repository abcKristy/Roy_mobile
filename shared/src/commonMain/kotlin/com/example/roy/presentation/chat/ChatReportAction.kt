package com.example.roy.presentation.chat


internal sealed class ChatReportAction {
    data object Close : ChatReportAction()
}
