package com.example.roy.data.points

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.network.encodeQueryValue
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointPage
import com.example.roy.domain.map.PointRepository
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.PointTopic
import com.example.roy.domain.map.Position
import com.example.roy.domain.map.ViewportQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal class PointRemoteDataSource(
    baseUrl: String,
    private val transport: AuthHttpTransport,
) : PointRepository {
    private val origin = baseUrl.trimEnd('/').also {
        val match = ApiOrigin.matchEntire(it)
        require(match != null && (match.groupValues[1].isEmpty() || match.groupValues[1].toInt() in 1..65535)) {
            "Invalid Point API origin"
        }
    }

    override suspend fun getViewportPage(query: ViewportQuery, cursor: String?): PointResult<PointPage> {
        val box = query.bbox
        val filter = query.filter
        val params = buildList {
            add("west=${box.west}")
            add("south=${box.south}")
            add("east=${box.east}")
            add("north=${box.north}")
            filter.topicKey?.let { add("topicKey=${it.encodeQueryValue()}") }
            filter.customTopicLabel?.let { add("customTopicLabel=${it.encodeQueryValue()}") }
            add("peopleMin=${filter.peopleMin ?: 0}")
            add("peopleMax=${filter.peopleMax ?: 50}")
            add("limit=30")
            cursor?.let { add("cursor=${it.encodeQueryValue()}") }
        }
        return get("points?${params.joinToString("&")}") { body ->
            val page = PointJson.decodeFromString<PointPageDto>(body)
            require(page.nextCursor == null || page.nextCursor.isNotBlank())
            PointPage(page.items.map(PointDto::toDomain), page.nextCursor)
        }
    }

    override suspend fun getPoint(pointId: String): PointResult<Point> {
        if (!PointId.matches(pointId)) return PointResult.Failure(PointError.UnexpectedResponse)
        return get("points/$pointId") { body ->
            val point = PointJson.decodeFromString<PointDto>(body).toDomain()
            require(point.id.equals(pointId, ignoreCase = true))
            point
        }
    }

    private suspend fun <T> get(path: String, decode: (String) -> T): PointResult<T> {
        val response = transport.execute(
            AuthHttpRequest(
                method = AuthHttpMethod.Get,
                url = "$origin/api/v1/$path",
                body = null,
                accessToken = null,
                maxResponseBytes = MaxPointResponseBytes,
            ),
        ) ?: return PointResult.Failure(PointError.Network)

        if (response.status != 200) {
            val code = try {
                PointJson.decodeFromString<PointErrorEnvelopeDto>(response.body).error.code
            } catch (_: IllegalArgumentException) {
                null
            }
            val error = when {
                response.status == 404 && code == "RESOURCE_GONE" -> PointError.ResourceGone
                response.status == 503 -> PointError.ServiceUnavailable
                response.status == 400 && code in setOf("INVALID_BBOX", "INVALID_CURSOR", "INVALID_FIELD") &&
                    path.startsWith("points?") -> PointError.InvalidViewport
                else -> PointError.UnexpectedResponse
            }
            return PointResult.Failure(error)
        }
        return try {
            PointResult.Success(decode(response.body))
        } catch (_: IllegalArgumentException) {
            PointResult.Failure(PointError.UnexpectedResponse)
        }
    }
}

@Serializable
private data class PointPageDto(val items: List<PointDto>, val nextCursor: String?)

@Serializable
private data class PointDto(
    val id: String,
    val position: PositionDto,
    val address: String,
    val addressSource: String,
    val peopleCount: Int,
    val topics: List<PointTopicDto>,
    val createdAt: String,
    val updatedAt: String,
) {
    fun toDomain(): Point {
        require(PointId.matches(id) && peopleCount >= 0)
        return Point(
            id, Position(latitude = position.lat, longitude = position.lon), address, addressSource,
            peopleCount, topics.map(PointTopicDto::toDomain), Instant.parse(createdAt), Instant.parse(updatedAt),
        )
    }
}

@Serializable
private data class PositionDto(val lat: Double, val lon: Double)

@Serializable
private data class PointTopicDto(
    val id: String,
    val presetKey: String,
    val label: String,
    val participantCount: Int,
    val chatId: String,
) {
    fun toDomain(): PointTopic {
        require(participantCount >= 0)
        return PointTopic(id, presetKey, label, participantCount, chatId)
    }
}

@Serializable
private data class PointErrorEnvelopeDto(val error: PointErrorDto)

@Serializable
private data class PointErrorDto(val code: String)

private val PointJson = Json { ignoreUnknownKeys = true }
private val PointId = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
private val ApiOrigin = Regex("""(?:https://[A-Za-z0-9.-]+|http://127\.0\.0\.1)(?::([0-9]{1,5}))?""")
private const val MaxPointResponseBytes = 2 * 1024 * 1024
