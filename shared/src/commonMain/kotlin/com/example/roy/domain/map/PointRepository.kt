package com.example.roy.domain.map

import kotlin.time.Instant
import kotlinx.serialization.Serializable

internal data class Point(
    val id: String,
    val position: Position,
    val address: String,
    val addressSource: String,
    val peopleCount: Int,
    val topics: List<PointTopic>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

internal data class PointTopic(
    val id: String,
    val presetKey: String,
    val label: String,
    val participantCount: Int,
    val chatId: String,
)

@Serializable
internal data class PointFilter(
    val topicKey: String? = null,
    val customTopicLabel: String? = null,
    val peopleMin: Int? = null,
    val peopleMax: Int? = null,
) {
    val isActive: Boolean
        get() = topicKey != null || customTopicLabel != null ||
            (peopleMin ?: 0) != 0 || (peopleMax ?: 50) != 50
}

internal data class ViewportQuery(
    val bbox: BoundingBox,
    val filter: PointFilter = PointFilter(),
)

internal data class PointPage(val items: List<Point>, val nextCursor: String?)

internal sealed interface PointResult<out T> {
    data class Success<T>(val value: T) : PointResult<T>
    data class Failure(val error: PointError) : PointResult<Nothing>
}

internal enum class PointError {
    Network,
    ResourceGone,
    ServiceUnavailable,
    InvalidViewport,
    UnexpectedResponse,
}

internal interface PointRepository {
    suspend fun getViewportPage(query: ViewportQuery, cursor: String?): PointResult<PointPage>
    suspend fun getPoint(pointId: String): PointResult<Point>
}
