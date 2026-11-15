package com.example.roy.domain.points

import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.PointTopic

internal interface PointDetailGateway {
    suspend fun point(pointId: String): PointResult<Point>
    suspend fun memberships(pointId: String, scopeId: Long, chatIds: Set<String>): AuthResult<Set<String>>
    suspend fun presets(): AuthResult<List<TopicPreset>>
    suspend fun add(pointId: String, scopeId: Long, topic: PointTopicInput, key: String): DetailReply<PointTopic>
    suspend fun join(chatId: String, scopeId: Long): DetailReply<Unit>
    suspend fun leave(chatId: String, scopeId: Long): DetailReply<Unit>
}

internal interface PointDetailRepository {
    suspend fun memberships(pointId: String, chatIds: Set<String>, token: String): AuthResult<Set<String>>
    suspend fun add(pointId: String, topic: PointTopicInput, key: String, token: String): AuthResult<DetailReply<PointTopic>>
    suspend fun join(chatId: String, token: String): AuthResult<DetailReply<Unit>>
    suspend fun leave(chatId: String, token: String): AuthResult<DetailReply<Unit>>
}

internal sealed interface DetailReply<out T> {
    data class Success<T>(val value: T) : DetailReply<T>
    data class AuthFailure(val error: AuthError) : DetailReply<Nothing>
    data object Gone : DetailReply<Nothing>
    data object Duplicate : DetailReply<Nothing>
    data object InvalidTopic : DetailReply<Nothing>
}

internal class PointDetailOperations(
    private val loadPoint: LoadPoint,
    private val session: SessionCoordinator,
    private val repository: PointDetailRepository,
    private val getPresets: suspend () -> AuthResult<List<TopicPreset>>,
) : PointDetailGateway {
    override suspend fun point(pointId: String) = loadPoint(pointId)

    override suspend fun memberships(pointId: String, scopeId: Long, chatIds: Set<String>) =
        session.readProtected(scopeId) { repository.memberships(pointId, chatIds, it) }

    override suspend fun presets() = getPresets()

    override suspend fun add(pointId: String, scopeId: Long, topic: PointTopicInput, key: String) =
        session.mutateProtected(scopeId) { repository.add(pointId, topic, key, it) }.reply()

    override suspend fun join(chatId: String, scopeId: Long) =
        session.mutateProtected(scopeId) { repository.join(chatId, it) }.reply()

    override suspend fun leave(chatId: String, scopeId: Long) =
        session.mutateProtected(scopeId) { repository.leave(chatId, it) }.reply()
}

internal fun Point.findTopic(input: PointTopicInput): PointTopic? = topics.find { topic ->
    topic.presetKey == input.presetKey && (
        topic.presetKey != OtherTopicKey ||
            topic.label.trim().topicComparisonKey() == input.customLabel?.trim()?.topicComparisonKey()
        )
}

// PostgreSQL lower() in the deployment locale folds each code point independently.
private fun String.topicComparisonKey(): String = buildString(length) {
    var index = 0
    while (index < this@topicComparisonKey.length) {
        val character = this@topicComparisonKey[index]
        if (character.isHighSurrogate() && this@topicComparisonKey.getOrNull(index + 1)?.isLowSurrogate() == true) {
            append(this@topicComparisonKey.substring(index, index + 2).lowercase())
            index += 2
        } else {
            append(character.lowercaseChar())
            index++
        }
    }
}

private fun <T> AuthResult<DetailReply<T>>.reply(): DetailReply<T> = when (this) {
    is AuthResult.Success -> value
    is AuthResult.Failure -> DetailReply.AuthFailure(error)
}
