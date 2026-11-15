package com.example.roy.domain.navigation

import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.Position

internal data class DraftContext(
    val weekRemaining: Int,
    val monthRemaining: Int,
    val nearbyAddresses: List<String>,
)

internal sealed interface ProtectedTargetResult<out T> {
    data class Ready<T>(val value: T) : ProtectedTargetResult<T>
    data object Gone : ProtectedTargetResult<Nothing>
    data object Forbidden : ProtectedTargetResult<Nothing>
    data object Unavailable : ProtectedTargetResult<Nothing>
}

internal interface ProtectedTargetRepository {
    suspend fun draft(position: Position, accessToken: String): AuthResult<ProtectedTargetResult<DraftContext>>
    suspend fun chatAccess(chatId: String, accessToken: String): AuthResult<ProtectedTargetResult<Unit>>
}

internal class ResolveProtectedTarget(
    private val loadPoint: LoadPoint,
    private val session: SessionCoordinator,
    private val repository: ProtectedTargetRepository,
) {
    suspend fun draft(position: Position, scopeId: Long): ProtectedTargetResult<DraftContext> =
        session.readProtected(scopeId) { repository.draft(position, it) }.targetResult()

    suspend fun topic(pointId: String, topicId: String, scopeId: Long): ProtectedTargetResult<String> {
        val point = when (val result = loadPoint(pointId)) {
            is PointResult.Success -> result.value
            is PointResult.Failure -> return if (result.error == PointError.ResourceGone) {
                ProtectedTargetResult.Gone
            } else {
                ProtectedTargetResult.Unavailable
            }
        }
        val topic = point.topics.firstOrNull { it.id == topicId } ?: return ProtectedTargetResult.Gone
        return when (session.readProtected(scopeId) { repository.chatAccess(topic.chatId, it) }.targetResult()) {
            is ProtectedTargetResult.Ready -> ProtectedTargetResult.Ready(topic.chatId)
            ProtectedTargetResult.Gone -> ProtectedTargetResult.Gone
            ProtectedTargetResult.Forbidden -> ProtectedTargetResult.Forbidden
            ProtectedTargetResult.Unavailable -> ProtectedTargetResult.Unavailable
        }
    }
}

private fun <T> AuthResult<ProtectedTargetResult<T>>.targetResult(): ProtectedTargetResult<T> =
    when (this) {
        is AuthResult.Success -> value
        is AuthResult.Failure -> when (error.code) {
            AuthErrorCode.AuthRequired, AuthErrorCode.SessionExpired -> ProtectedTargetResult.Forbidden
            else -> ProtectedTargetResult.Unavailable
        }
    }
