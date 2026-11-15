@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.roy.data.chat

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthJson
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.chat.ChatReportReply
import com.example.roy.domain.chat.ChatSendReply
import com.example.roy.domain.chat.ChatWriteRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ChatWriteRemoteDataSource(
    baseUrl: String,
    private val transport: AuthHttpTransport,
) : ChatWriteRepository {
    private val origin = baseUrl.trimEnd('/')

    override suspend fun send(
        chatId: String,
        text: String,
        key: String,
        token: String,
    ): AuthResult<ChatSendReply> {
        if (Uuid.parseOrNull(chatId) == null) return failure(AuthErrorCode.UnexpectedResponse)
        val response = post("chats/$chatId/messages", AuthJson.encodeToString(SendBody(text)), key, token)
            ?: return failure(AuthErrorCode.Network)
        if (response.status == 201) {
            val message = decode { AuthJson.decodeFromString<ChatMessageDto>(response.body).toDomain() }
                ?.takeIf { it.chatId == chatId } ?: return failure(AuthErrorCode.UnexpectedResponse)
            return AuthResult.Success(ChatSendReply.Sent(message))
        }
        val error = response.error()
        val reply = when (response.status to error?.code) {
            403 to "NOT_MEMBER" -> ChatSendReply.NotMember
            404 to "RESOURCE_GONE" -> ChatSendReply.Gone
            403 to "MESSAGE_MUTED" -> error?.detail("mutedUntil")?.let { decode { Instant.parse(it) } }
                ?.let { ChatSendReply.Muted(it) }
            429 to "MESSAGE_FLOOD" -> (response.retryAfter?.toLongOrNull() ?: error?.retryAfterSeconds)
                ?.takeIf { it > 0 }?.let { ChatSendReply.Flood(it) }
            422 to "MESSAGE_REJECTED_PATTERN" -> ChatSendReply.Rejected(error?.detail("category"))
            422 to "INVALID_FIELD" -> ChatSendReply.InvalidText
            409 to "IDEMPOTENCY_KEY_REUSED" -> ChatSendReply.KeyReused
            else -> null
        }
        return reply?.let { AuthResult.Success(it) } ?: response.failure()
    }

    override suspend fun report(
        chatId: String,
        targetUserId: String,
        reason: String,
        key: String,
        token: String,
    ): AuthResult<ChatReportReply> {
        if (Uuid.parseOrNull(chatId) == null || Uuid.parseOrNull(targetUserId) == null) {
            return AuthResult.Success(ChatReportReply.Forbidden)
        }
        val body = AuthJson.encodeToString(ReportBody(chatId, targetUserId, reason))
        val response = post("reports", body, key, token) ?: return failure(AuthErrorCode.Network)
        if (response.status == 201) return AuthResult.Success(ChatReportReply.Submitted)
        val reply = when (response.status to response.error()?.code) {
            409 to "DUPLICATE_REPORT" -> ChatReportReply.Duplicate
            403 to "FORBIDDEN" -> ChatReportReply.Forbidden
            422 to "INVALID_FIELD" -> ChatReportReply.InvalidReason
            409 to "IDEMPOTENCY_KEY_REUSED" -> ChatReportReply.KeyReused
            else -> null
        }
        return reply?.let { AuthResult.Success(it) } ?: response.failure()
    }

    private suspend fun post(path: String, body: String, key: String, token: String) = transport.execute(
        AuthHttpRequest(
            AuthHttpMethod.Post,
            "$origin/api/v1/$path",
            body,
            token,
            headers = mapOf("Idempotency-Key" to key),
        ),
    )
}

@Serializable
private data class SendBody(val text: String)

@Serializable
private data class ReportBody(val contextChatId: String, val targetUserId: String, val reasonText: String)

@Serializable
private data class WriteErrorEnvelope(val error: WriteError)

@Serializable
private data class WriteError(val code: String, val details: JsonObject? = null, val retryAfterSeconds: Long? = null) {
    fun detail(name: String): String? = (details?.get(name) as? JsonPrimitive)?.contentOrNull
}

private fun AuthHttpResponse.error() = decode { AuthJson.decodeFromString<WriteErrorEnvelope>(body).error }

private fun AuthHttpResponse.failure() = failure(when (status) {
    401 -> AuthErrorCode.AuthRequired
    in 500..599 -> AuthErrorCode.ServiceUnavailable
    else -> AuthErrorCode.UnexpectedResponse
})

private fun failure(code: AuthErrorCode) = AuthResult.Failure(AuthError(code))

private inline fun <T> decode(block: () -> T): T? = try {
    block()
} catch (_: IllegalArgumentException) {
    null
}
