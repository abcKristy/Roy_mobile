package com.example.roy.data.chat

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthJson
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.chat.ChatListItem
import com.example.roy.domain.chat.ChatListPage
import com.example.roy.domain.chat.ChatListRepository
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal class ChatListRemoteDataSource(
    baseUrl: String,
    private val transport: AuthHttpTransport,
) : ChatListRepository {
    private val origin = baseUrl.trimEnd('/')

    override suspend fun page(cursor: String?, token: String): AuthResult<ChatListPage> {
        if (cursor != null && !ListCursor.matches(cursor)) return listFailure()
        val suffix = cursor?.let { "&cursor=$it" }.orEmpty()
        val request = AuthHttpRequest(
            AuthHttpMethod.Get, "$origin/api/v1/me/chats?limit=30$suffix", null, token, 2 * 1024 * 1024,
        )
        return request(request) { body ->
            val page = AuthJson.decodeFromString<ListPageDto>(body)
            require(page.nextCursor == null || page.nextCursor != cursor && ListCursor.matches(page.nextCursor))
            ChatListPage(page.items.map(ListRowDto::toDomain), page.nextCursor)
        }
    }

    override suspend fun advance(chatId: String, sequence: Long, token: String): AuthResult<Long> {
        if (!ChatUuid.matches(chatId) || sequence <= 0) return listFailure()
        return request(
            AuthHttpRequest(
                AuthHttpMethod.Put, "$origin/api/v1/chats/$chatId/read-cursor",
                AuthJson.encodeToString(ReadCursorDto(sequence)), token,
            ),
        ) { body ->
            val membership = AuthJson.decodeFromString<ListMembershipDto>(body)
            require(membership.chatId == chatId && membership.lastReadMessageSeq >= sequence)
            membership.lastReadMessageSeq
        }
    }

    private suspend fun <T> request(request: AuthHttpRequest, decode: (String) -> T): AuthResult<T> {
        val response = transport.execute(request) ?: return listFailure(AuthErrorCode.Network)
        if (response.status != 200) {
            return listFailure(when (response.status) {
                401 -> AuthErrorCode.AuthRequired
                422 -> AuthErrorCode.InvalidField
                429 -> AuthErrorCode.RateLimited
                in 500..599 -> AuthErrorCode.ServiceUnavailable
                else -> AuthErrorCode.UnexpectedResponse
            })
        }
        return try {
            AuthResult.Success(decode(response.body))
        } catch (_: IllegalArgumentException) {
            listFailure()
        }
    }
}

private fun listFailure(code: AuthErrorCode = AuthErrorCode.UnexpectedResponse) = AuthResult.Failure(AuthError(code))

@Serializable
private data class ListPageDto(val items: List<ListRowDto>, val nextCursor: String?)

@Serializable
private data class ListRowDto(
    val id: String,
    val pointId: String,
    val topic: ListTopicDto,
    val address: String,
    val lastMessage: ListPreviewDto?,
    val updatedAt: String,
    val unreadCount: Long,
) {
    fun toDomain(): ChatListItem {
        require(ChatUuid.matches(id) && ChatUuid.matches(pointId) && ChatUuid.matches(topic.id))
        require(topic.chatId == id && unreadCount >= 0)
        require(lastMessage == null || lastMessage.type == "system" || lastMessage.type == "text")
        return ChatListItem(
            id, pointId, topic.id, topic.presetKey, topic.label, address,
            lastMessage?.text, lastMessage?.let { Instant.parse(it.createdAt) }, Instant.parse(updatedAt), unreadCount,
        )
    }
}

@Serializable
private data class ListTopicDto(val id: String, val chatId: String, val presetKey: String, val label: String)
@Serializable
private data class ListPreviewDto(val type: String, val text: String, val createdAt: String)
@Serializable
private data class ReadCursorDto(val lastReadMessageSeq: Long)
@Serializable
private data class ListMembershipDto(val chatId: String, val lastReadMessageSeq: Long)

private val ListCursor = Regex("[A-Za-z0-9_-]+[.][A-Za-z0-9_-]+")
private val ChatUuid = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
