package com.example.roy.domain.chat

internal sealed interface ChatRealtimeEvent {
    data object Resync : ChatRealtimeEvent
    data object ChatsChanged : ChatRealtimeEvent
    data class MessageCreated(val message: ChatMessage) : ChatRealtimeEvent
    data class AccessLost(val chatId: String) : ChatRealtimeEvent
}
