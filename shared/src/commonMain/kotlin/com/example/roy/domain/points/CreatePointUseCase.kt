package com.example.roy.domain.points

import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.map.Position
import kotlin.time.Instant
import kotlinx.serialization.Serializable

internal const val OTHER_TOPIC_KEY = "other"
internal const val MAX_CUSTOM_TOPIC_LENGTH = 40
internal const val MAX_POINT_ADDRESS_LENGTH = 300

internal data class TopicPreset(val key: String, val label: String)

@Serializable
internal data class PointTopicInput(val presetKey: String, val customLabel: String? = null)

internal enum class PointAddressSource(val apiValue: String) {
    Provider("provider"),
    Manual("manual"),
}

internal data class CreatePointDraft(
    val position: Position,
    val topic: PointTopicInput? = null,
    val address: String = "",
    val addressSource: PointAddressSource? = null,
)

internal data class CreatePointPayload(
    val position: Position,
    val topic: PointTopicInput,
    val address: String,
    val addressSource: PointAddressSource,
)

internal data class CreatePointAttempt(
    val payload: CreatePointPayload,
    val key: String,
    val confirmNearby: Boolean = false,
)

internal data class NearbyPoint(
    val id: String,
    val position: Position,
    val address: String,
    val distanceMeters: Double,
)

internal data class PointCreationQuota(
    val weekUsed: Int,
    val weekLimit: Int,
    val weekRemaining: Int,
    val weekNextAvailableAt: Instant?,
    val monthUsed: Int,
    val monthLimit: Int,
    val monthRemaining: Int,
    val monthNextAvailableAt: Instant?,
    val blockedUntil: Instant?,
    val lastAvailableWarning: Boolean,
)

internal data class CreationContext(val nearby: List<NearbyPoint>, val quota: PointCreationQuota)

internal data class CreatedPointDestination(
    val pointId: String,
    val topicId: String,
    val chatId: String,
    val label: String,
)

internal enum class CreatePointField {
    Position,
    Topic,
    Address,
}

internal sealed interface CreatePointOutcome {
    data class Created(val destination: CreatedPointDestination, val quota: PointCreationQuota) : CreatePointOutcome
    data class ConfirmNearby(val points: List<NearbyPoint>) : CreatePointOutcome
    data class Blocked(val until: Instant) : CreatePointOutcome
    data class Invalid(val field: CreatePointField?) : CreatePointOutcome
    data object KeyReused : CreatePointOutcome
}

internal interface CreatePointRepository {
    suspend fun getPresets(): AuthResult<List<TopicPreset>>
    suspend fun getContext(position: Position, accessToken: String): AuthResult<CreationContext>
    suspend fun getAddress(position: Position, accessToken: String): AuthResult<String?>
    suspend fun create(attempt: CreatePointAttempt, accessToken: String): AuthResult<CreatePointOutcome>
}

internal class CreatePointUseCase(
    private val repository: CreatePointRepository,
    private val session: SessionCoordinator,
    private val scopeId: Long,
) {
    suspend fun getPresets() = repository.getPresets()

    suspend fun getContext(position: Position) =
        session.readProtected(expectedScopeId = scopeId) { repository.getContext(position, it) }

    suspend fun getAddress(position: Position) =
        session.readProtected(expectedScopeId = scopeId) { repository.getAddress(position, it) }

    suspend fun submit(attempt: CreatePointAttempt): AuthResult<CreatePointOutcome> {
        val result = session.mutateProtected(scopeId) { repository.create(attempt, it) }
        val wasRejected = result is AuthResult.Failure &&
            result.error.code in setOf(AuthErrorCode.SessionExpired, AuthErrorCode.AuthRequired)
        return if (wasRejected && session.state.value == SessionStatus.Active(scopeId)) {
            AuthResult.Failure(AuthError(AuthErrorCode.Network))
        } else result
    }

    fun validatedPayload(draft: CreatePointDraft, presets: List<TopicPreset>): CreatePointPayload? {
        val topic = draft.topic ?: return null
        val normalizedTopic = if (topic.presetKey == OTHER_TOPIC_KEY) {
            val label = topic.customLabel?.trim()?.takeIf(::validCustomTopicLabel) ?: return null
            topic.copy(customLabel = label)
        } else {
            if (presets.none { it.key == topic.presetKey }) return null
            topic.copy(customLabel = null)
        }
        val address = draft.address.trim().takeIf(::validPointAddress) ?: return null
        val source = draft.addressSource ?: return null
        return CreatePointPayload(draft.position, normalizedTopic, address, source)
    }
}

internal fun validCustomTopicLabel(label: String): Boolean = validPointText(label, MAX_CUSTOM_TOPIC_LENGTH)
internal fun validPointAddress(address: String): Boolean = validPointText(address, MAX_POINT_ADDRESS_LENGTH)

private fun validPointText(value: String, limit: Int): Boolean {
    val trimmed = value.trim()
    return '\u0000' !in trimmed && trimmed.codePointLength() in 1..limit
}

internal fun String.codePointLength(): Int {
    var length = 0
    var index = 0
    while (index < this.length) {
        if (this[index].isHighSurrogate() && getOrNull(index + 1)?.isLowSurrogate() == true) index++
        index++
        length++
    }
    return length
}
