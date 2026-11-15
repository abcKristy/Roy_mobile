package com.example.roy.data.chat

import com.example.roy.domain.chat.ChatMessage
import com.example.roy.domain.chat.ChatMessageAuthor
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatMessageDto(
    val id: String,
    val chatId: String,
    val messageSeq: Long,
    val type: String,
    val author: ChatMessageAuthorDto? = null,
    val text: String? = null,
    val system: ChatMessageSystemDto? = null,
    val createdAt: String,
) {
    fun toDomain(): ChatMessage {
        require(messageSeq > 0 && id.isNotBlank() && chatId.isNotBlank())
        return ChatMessage(
            id = id,
            chatId = chatId,
            messageSeq = messageSeq,
            type = type,
            author = author?.let { ChatMessageAuthor(it.id, it.displayName, it.avatarUrl) },
            text = text,
            systemKind = system?.kind,
            createdAt = Instant.parse(createdAt),
        )
    }
}

@Serializable
internal data class ChatMessageAuthorDto(val id: String?, val displayName: String, val avatarUrl: String? = null)

@Serializable
internal data class ChatMessageSystemDto(val kind: String? = null)
