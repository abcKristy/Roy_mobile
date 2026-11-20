package com.example.roy.presentation.chats


internal sealed class ChatListViewEvent {
    data object OnRefresh : ChatListViewEvent()
    data object OnRetry : ChatListViewEvent()
    data object OnLoadMore : ChatListViewEvent()
    data object OnOpenMap : ChatListViewEvent()
    data class OnOpenChat(val chatId: String) : ChatListViewEvent()
    data class OnMembershipLost(val chatId: String) : ChatListViewEvent()
}
