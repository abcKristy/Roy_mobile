package com.example.roy.domain.chat

import com.example.roy.domain.auth.AuthResult
import kotlin.time.Instant

internal interface ChatHistoryRepository {
    suspend fun detail(chatId: String, token: String): AuthResult<ChatReadReply<ChatDetail>>
    suspend fun history(
        chatId: String,
        token: String,
        beforeSeq: Long? = null,
        afterSeq: Long? = null,
    ): AuthResult<ChatReadReply<ChatHistoryPage>>
    suspend fun search(
        chatId: String,
        token: String,
        query: String,
        cursor: String? = null,
    ): AuthResult<ChatReadReply<ChatSearchPage>>
}

internal sealed class ChatReadReply<out T> {
    data class Data<T>(val value: T) : ChatReadReply<T>()
    data object NotMember : ChatReadReply<Nothing>()
    data object Gone : ChatReadReply<Nothing>()
}

internal data class ChatDetail(
    val id: String,
    val pointId: String,
    val topicId: String,
    val topicLabel: String,
    val address: String,
    val userId: String,
    val lastReadMessageSeq: Long,
    val lastMessageSeq: Long,
    val currentProposalId: String? = null,
    val currentProposalRevision: Long? = null,
)

internal data class ChatMessage(
    val id: String,
    val chatId: String,
    val messageSeq: Long,
    val type: String,
    val author: ChatMessageAuthor?,
    val text: String?,
    val systemKind: String?,
    val createdAt: Instant,
    val systemProposalId: String? = null,
    val systemReason: String? = null,
)

internal data class ChatMessageAuthor(val id: String?, val displayName: String, val avatarUrl: String?)
internal data class ChatHistoryPage(val items: List<ChatMessage>, val nextBeforeSeq: Long?, val nextAfterSeq: Long?)
internal data class ChatSearchPage(val items: List<ChatMessage>, val nextCursor: String?)
