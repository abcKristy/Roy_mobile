package com.example.roy.data.chat

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthJson
import com.example.roy.data.network.encodeQueryValue
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.chat.ChatDetail
import com.example.roy.domain.chat.ChatHistoryPage
import com.example.roy.domain.chat.ChatHistoryRepository
import com.example.roy.domain.chat.ChatReadReply
import com.example.roy.domain.chat.ChatSearchPage
import kotlinx.serialization.Serializable

internal class ChatHistoryRemoteDataSource(
    baseUrl: String,
    private val transport: AuthHttpTransport,
) : ChatHistoryRepository {
    private val origin = baseUrl.trimEnd('/')

    override suspend fun detail(chatId: String, token: String): AuthResult<ChatReadReply<ChatDetail>> =
        read(chatId, "", token) { body ->
            val dto = AuthJson.decodeFromString<ChatDetailDto>(body)
            require(dto.id == chatId && dto.topic.chatId == chatId && dto.membership.chatId == chatId)
            require(dto.lastMessageSeq >= dto.membership.lastReadMessageSeq && dto.membership.lastReadMessageSeq >= 0)
            ChatDetail(
                dto.id, dto.pointId, dto.topic.id, dto.topic.label, dto.address, dto.membership.userId,
                dto.membership.lastReadMessageSeq, dto.lastMessageSeq,
            )
        }

    override suspend fun history(
        chatId: String,
        token: String,
        beforeSeq: Long?,
        afterSeq: Long?,
    ): AuthResult<ChatReadReply<ChatHistoryPage>> {
        val cursor = when {
            beforeSeq != null -> "&beforeSeq=$beforeSeq"
            afterSeq != null -> "&afterSeq=$afterSeq"
            else -> ""
        }
        return read(chatId, "/messages?limit=30$cursor", token) { body ->
            val dto = AuthJson.decodeFromString<ChatHistoryDto>(body)
            require(dto.items.all { it.chatId == chatId })
            require(dto.items.all { (beforeSeq == null || it.messageSeq < beforeSeq) &&
                (afterSeq == null || it.messageSeq > afterSeq) })
            require(dto.nextBeforeSeq == null || (afterSeq == null && dto.nextBeforeSeq == dto.items.lastOrNull()?.messageSeq))
            require(dto.nextAfterSeq == null || (afterSeq != null && dto.nextAfterSeq == dto.items.lastOrNull()?.messageSeq))
            ChatHistoryPage(dto.items.map(ChatMessageDto::toDomain), dto.nextBeforeSeq, dto.nextAfterSeq)
        }
    }

    override suspend fun search(
        chatId: String,
        token: String,
        query: String,
        cursor: String?,
    ): AuthResult<ChatReadReply<ChatSearchPage>> {
        val suffix = cursor?.let { "&cursor=${it.encodeQueryValue()}" }.orEmpty()
        return read(chatId, "/messages/search?limit=30&q=${query.encodeQueryValue()}$suffix", token) { body ->
            val dto = AuthJson.decodeFromString<ChatSearchDto>(body)
            require(dto.items.all { it.chatId == chatId })
            require(dto.nextCursor == null || dto.nextCursor != cursor)
            ChatSearchPage(dto.items.map(ChatMessageDto::toDomain), dto.nextCursor)
        }
    }

    private suspend fun <T> read(
        chatId: String,
        path: String,
        token: String,
        decode: (String) -> T,
    ): AuthResult<ChatReadReply<T>> {
        if (!CHAT_ID.matches(chatId)) return failure(AuthErrorCode.UnexpectedResponse)
        val response = transport.execute(
            AuthHttpRequest(AuthHttpMethod.Get, "$origin/api/v1/chats/$chatId$path", null, token, 2 * 1024 * 1024),
        ) ?: return failure(AuthErrorCode.Network)
        if (response.status == 200) {
            val result = parse { decode(response.body) } ?: return failure(AuthErrorCode.UnexpectedResponse)
            return AuthResult.Success(ChatReadReply.Data(result))
        }
        val code = parse { AuthJson.decodeFromString<ChatReadErrorEnvelope>(response.body).error.code }
        return when {
            response.status == 403 && code == "NOT_MEMBER" -> AuthResult.Success(ChatReadReply.NotMember)
            response.status == 404 && code == "RESOURCE_GONE" -> AuthResult.Success(ChatReadReply.Gone)
            response.status == 401 && code == "AUTH_REQUIRED" -> failure(AuthErrorCode.AuthRequired)
            response.status == 401 && code == "SESSION_EXPIRED" -> failure(AuthErrorCode.SessionExpired)
            response.status in 500..599 -> failure(AuthErrorCode.ServiceUnavailable)
            else -> failure(AuthErrorCode.UnexpectedResponse)
        }
    }
}

private inline fun <T> parse(decode: () -> T): T? = try {
    decode()
} catch (_: IllegalArgumentException) {
    null
}

private fun failure(code: AuthErrorCode): AuthResult.Failure = AuthResult.Failure(AuthError(code))

@Serializable
private data class ChatDetailDto(
    val id: String,
    val pointId: String,
    val topic: ChatDetailTopicDto,
    val address: String,
    val membership: ChatDetailMembershipDto,
    val lastMessageSeq: Long,
)

@Serializable
private data class ChatDetailTopicDto(val id: String, val label: String, val chatId: String)

@Serializable
private data class ChatDetailMembershipDto(val chatId: String, val userId: String, val lastReadMessageSeq: Long)

@Serializable
private data class ChatHistoryDto(val items: List<ChatMessageDto>, val nextBeforeSeq: Long?, val nextAfterSeq: Long?)

@Serializable
private data class ChatSearchDto(val items: List<ChatMessageDto>, val nextCursor: String?)

@Serializable
private data class ChatReadErrorEnvelope(val error: ChatReadErrorDto)

@Serializable
private data class ChatReadErrorDto(val code: String)

private val CHAT_ID = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
