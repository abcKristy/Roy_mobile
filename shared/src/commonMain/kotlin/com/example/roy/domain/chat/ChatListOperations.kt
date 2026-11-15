package com.example.roy.domain.chat

import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

internal data class ChatListItem(
    val chatId: String,
    val pointId: String,
    val topicId: String,
    val presetKey: String,
    val topic: String,
    val address: String,
    val preview: String?,
    val lastMessageAt: Instant?,
    val updatedAt: Instant,
    val unreadCount: Long,
)

internal data class ChatListPage(val items: List<ChatListItem>, val nextCursor: String?)

internal interface ChatListRepository {
    suspend fun page(cursor: String?, token: String): AuthResult<ChatListPage>
    suspend fun advance(chatId: String, sequence: Long, token: String): AuthResult<Long>
}

internal class ChatListOperations(
    private val sessions: SessionCoordinator,
    private val repository: ChatListRepository,
) {
    suspend fun page(scopeId: Long, cursor: String?) =
        sessions.readProtected(scopeId) { repository.page(cursor, it) }

    suspend fun advance(scopeId: Long, chatId: String, sequence: Long) =
        sessions.mutateProtected(scopeId) { repository.advance(chatId, sequence, it) }
}

internal class ChatReadCursor(private val write: suspend (Long) -> AuthResult<Long>) {
    constructor(scopeId: Long, chatId: String, operations: ChatListOperations) :
        this({ operations.advance(scopeId, chatId, it) })

    private val gate = Mutex()
    private var acknowledged = 0L

    suspend fun advance(visibleSeq: Long, lastKnownSeq: Long): AuthResult<Long> = gate.withLock {
        if (visibleSeq <= acknowledged || visibleSeq > lastKnownSeq) {
            return@withLock AuthResult.Success(acknowledged)
        }
        when (val result = write(visibleSeq)) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> {
                if (result.value < visibleSeq) return@withLock AuthResult.Failure(AuthError(AuthErrorCode.UnexpectedResponse))
                acknowledged = result.value
                result
            }
        }
    }
}

internal fun chatUnreadBadge(count: Long): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}
