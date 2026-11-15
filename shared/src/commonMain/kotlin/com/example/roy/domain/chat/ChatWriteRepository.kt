package com.example.roy.domain.chat

import com.example.roy.domain.auth.AuthResult
import kotlin.time.Instant

internal interface ChatWriteRepository {
    suspend fun send(chatId: String, text: String, key: String, token: String): AuthResult<ChatSendReply>

    suspend fun report(
        chatId: String,
        targetUserId: String,
        reason: String,
        key: String,
        token: String,
    ): AuthResult<ChatReportReply>
}

internal sealed interface ChatSendReply {
    data class Sent(val message: ChatMessage) : ChatSendReply
    data class Muted(val until: Instant) : ChatSendReply
    data class Flood(val retryAfterSeconds: Long) : ChatSendReply
    data class Rejected(val category: String?) : ChatSendReply
    data object NotMember : ChatSendReply
    data object Gone : ChatSendReply
    data object InvalidText : ChatSendReply
    data object KeyReused : ChatSendReply
}

internal enum class ChatReportReply {
    Submitted,
    Duplicate,
    Forbidden,
    InvalidReason,
    KeyReused,
}
