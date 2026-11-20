package com.example.roy.presentation.chats

import com.example.roy.domain.chat.ChatListItem

internal sealed class ChatListViewAction {
    data object OpenMap : ChatListViewAction()
    data class OpenChat(val row: ChatListItem) : ChatListViewAction()
}
