package com.example.roy.data.points

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthJson
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.map.PointTopic
import com.example.roy.domain.points.DetailReply
import com.example.roy.domain.points.PointDetailRepository
import com.example.roy.domain.points.PointTopicInput
import kotlinx.serialization.Serializable

internal class PointDetailRemoteDataSource(
    baseUrl: String,
    private val transport: AuthHttpTransport,
) : PointDetailRepository {
    private val origin = baseUrl.trimEnd('/')

    override suspend fun memberships(pointId: String, chatIds: Set<String>, token: String): AuthResult<Set<String>> {
        if (chatIds.isEmpty()) return AuthResult.Success(emptySet())
        val members = mutableSetOf<String>()
        val cursors = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val suffix = cursor?.let { "&cursor=${it}" }.orEmpty()
            val response = transport.execute(
                AuthHttpRequest(AuthHttpMethod.Get, "$origin/api/v1/me/chats?limit=30$suffix", null, token, 2 * 1024 * 1024),
            ) ?: return failure(AuthErrorCode.Network)
            if (response.status != 200) return failure(response.status.toAuthError())
            val page = deserialize { AuthJson.decodeFromString<MembershipPageDto>(response.body) }
                ?: return failure(AuthErrorCode.UnexpectedResponse)
            for (chat in page.items) if (chat.pointId == pointId && chat.id in chatIds) members += chat.id
            cursor = page.nextCursor
            if (cursor != null && (!CursorToken.matches(cursor) || !cursors.add(cursor))) {
                return failure(AuthErrorCode.UnexpectedResponse)
            }
        } while (cursor != null && members.size < chatIds.size)
        return AuthResult.Success(members.toSet())
    }

    override suspend fun add(pointId: String, topic: PointTopicInput, key: String, token: String) = mutate(
        id = pointId,
        method = AuthHttpMethod.Post,
        path = "points/$pointId/topics",
        token = token,
        body = AuthJson.encodeToString(AddTopicDto(topic)),
        headers = mapOf("Idempotency-Key" to key),
        successStatus = 201,
    ) { body ->
        val result = AuthJson.decodeFromString<AddedTopicDto>(body)
        val created = result.topic
        require(result.chat.pointId == pointId && created.chatId == result.chat.id)
        require(result.membership.chatId == created.chatId && created.participantCount > 0)
        require(created.presetKey == topic.presetKey && UuidString.matches(created.id) && UuidString.matches(created.chatId))
        PointTopic(created.id, created.presetKey, created.label, created.participantCount, created.chatId)
    }

    override suspend fun join(chatId: String, token: String) = mutate(
        chatId, AuthHttpMethod.Put, "chats/$chatId/membership", token, successStatus = 200,
    ) { body ->
        require(AuthJson.decodeFromString<MembershipDto>(body).chatId == chatId)
    }

    override suspend fun leave(chatId: String, token: String) = mutate(
        chatId, AuthHttpMethod.Delete, "chats/$chatId/membership", token, successStatus = 204,
    ) { Unit }

    private suspend fun <T> mutate(
        id: String,
        method: AuthHttpMethod,
        path: String,
        token: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        successStatus: Int,
        decode: (String) -> T,
    ): AuthResult<DetailReply<T>> {
        if (!UuidString.matches(id)) return failure(AuthErrorCode.UnexpectedResponse)
        val response = transport.execute(
            AuthHttpRequest(method, "$origin/api/v1/$path", body, token, headers = headers),
        ) ?: return failure(AuthErrorCode.Network)
        if (response.status == successStatus) {
            val result = deserialize { decode(response.body) } ?: return failure(AuthErrorCode.UnexpectedResponse)
            return AuthResult.Success(DetailReply.Success(result))
        }
        val code = deserialize { AuthJson.decodeFromString<DetailErrorEnvelopeDto>(response.body).error.code }
        return when {
            response.status == 404 && code == "RESOURCE_GONE" -> AuthResult.Success(DetailReply.Gone)
            response.status == 409 && code == "DUPLICATE_TOPIC" -> AuthResult.Success(DetailReply.Duplicate)
            response.status == 422 && code == "INVALID_FIELD" -> AuthResult.Success(DetailReply.InvalidTopic)
            else -> failure(response.status.toAuthError())
        }
    }
}

private inline fun <T> deserialize(decode: () -> T): T? = try {
    decode()
} catch (_: IllegalArgumentException) {
    null
}

private fun Int.toAuthError() = when (this) {
    401 -> AuthErrorCode.AuthRequired
    429 -> AuthErrorCode.RateLimited
    in 500..599 -> AuthErrorCode.ServiceUnavailable
    else -> AuthErrorCode.UnexpectedResponse
}

private fun failure(code: AuthErrorCode) = AuthResult.Failure(AuthError(code))

@Serializable
private data class MembershipPageDto(val items: List<MemberChatDto>, val nextCursor: String?)
@Serializable
private data class MemberChatDto(val id: String, val pointId: String)
@Serializable
private data class AddTopicDto(val topic: PointTopicInput)
@Serializable
private data class AddedTopicDto(val topic: AddedPointTopicDto, val chat: MemberChatDto, val membership: MembershipDto)
@Serializable
private data class AddedPointTopicDto(
    val id: String,
    val presetKey: String,
    val label: String,
    val participantCount: Int,
    val chatId: String,
)
@Serializable
private data class MembershipDto(val chatId: String)
@Serializable
private data class DetailErrorEnvelopeDto(val error: DetailErrorDto)
@Serializable
private data class DetailErrorDto(val code: String)

private val CursorToken = Regex("[A-Za-z0-9_-]+[.][A-Za-z0-9_-]+")
private val UuidString = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
