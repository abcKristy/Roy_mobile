package com.example.roy.domain.chat

internal sealed interface ChatRealtimeEvent {
    data object Resync : ChatRealtimeEvent
    data object ChatsChanged : ChatRealtimeEvent
    data object NotificationsChanged : ChatRealtimeEvent
    data class MessageCreated(val message: ChatMessage) : ChatRealtimeEvent
    data class AccessLost(val chatId: String) : ChatRealtimeEvent
    data class MeetingChanged(
        val chatId: String,
        val proposalId: String,
        val revision: Long?,
        val kind: MeetingChangeKind,
    ) : ChatRealtimeEvent
}

internal enum class MeetingChangeKind {
    ProposalUpdated,
    BookingChanged,
}
