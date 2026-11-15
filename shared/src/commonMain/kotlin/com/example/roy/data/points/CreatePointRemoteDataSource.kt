package com.example.roy.data.points

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.map.Position
import com.example.roy.domain.points.CreatePointAttempt
import com.example.roy.domain.points.CreatePointField
import com.example.roy.domain.points.CreatePointOutcome
import com.example.roy.domain.points.CreatePointRepository
import com.example.roy.domain.points.CreatedPointDestination
import com.example.roy.domain.points.CreationContext
import com.example.roy.domain.points.NearbyPoint
import com.example.roy.domain.points.PointCreationQuota
import com.example.roy.domain.points.PointTopicInput
import com.example.roy.domain.points.TopicPreset
import com.example.roy.domain.points.validPointAddress
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class CreatePointRemoteDataSource(
    baseUrl: String,
    private val transport: AuthHttpTransport,
) : CreatePointRepository {
    private val origin = baseUrl.trimEnd('/').also {
        val match = CreateApiOrigin.matchEntire(it)
        require(match != null && (match.groupValues[1].isEmpty() || match.groupValues[1].toInt() in 1..65535)) {
            "Invalid Point API origin"
        }
    }

    override suspend fun getPresets(): AuthResult<List<TopicPreset>> = request("topics/presets", expected = 200) {
        CreateJson.decodeFromString<PresetListDto>(it).items.map { item ->
            require(item.key.isNotBlank() && item.label.isNotBlank())
            TopicPreset(item.key, item.label)
        }.distinctBy(TopicPreset::key)
    }

    override suspend fun getContext(position: Position, accessToken: String): AuthResult<CreationContext> =
        request("points/creation-context?lat=${position.latitude}&lon=${position.longitude}", accessToken, expected = 200) {
            val dto = CreateJson.decodeFromString<ContextDto>(it)
            CreationContext(dto.nearby.map(NearbyDto::toDomain), dto.quota.toDomain())
        }

    override suspend fun getAddress(position: Position, accessToken: String): AuthResult<String?> = request(
        path = "geocoding/reverse",
        accessToken = accessToken,
        requestBody = CreateJson.encodeToString(ReverseRequestDto(position.toDto())),
        expected = 200,
    ) {
        val dto = CreateJson.decodeFromString<ReverseResponseDto>(it)
        dto.address?.takeIf { address -> dto.source == "provider" && validPointAddress(address) }
    }

    override suspend fun create(attempt: CreatePointAttempt, accessToken: String): AuthResult<CreatePointOutcome> {
        val payload = attempt.payload
        val body = CreateJson.encodeToString(
            CreatePointDto(
                position = payload.position.toDto(),
                topic = payload.topic,
                address = payload.address,
                addressSource = payload.addressSource.apiValue,
                confirmNearby = attempt.confirmNearby,
            ),
        )
        val response = execute("points", accessToken, body, mapOf("Idempotency-Key" to attempt.key))
            ?: return failure(AuthErrorCode.Network)
        if (response.status == 201) return decode {
            val data = CreateJson.decodeFromString<CreatedPointDto>(response.body)
            val chat = data.chat
            require(chat.pointId == data.point.id && chat.topic.chatId == chat.id)
            require(listOf(data.point.id, chat.id, chat.topic.id).all(CreatePointId::matches))
            CreatePointOutcome.Created(
                CreatedPointDestination(data.point.id, chat.topic.id, chat.id, chat.topic.label),
                data.quota.toDomain(),
            )
        }
        return decodeOutcome(response)
    }

    private fun decodeOutcome(response: AuthHttpResponse): AuthResult<CreatePointOutcome> {
        val error = response.error()
        return when (error?.code) {
            "NEARBY_CONFIRM_REQUIRED" -> decode {
                val list = requireNotNull(error.details?.get("nearby"))
                CreatePointOutcome.ConfirmNearby(CreateJson.decodeFromJsonElement(
                    ListSerializer(NearbyDto.serializer()), list,
                ).map(NearbyDto::toDomain))
            }
            "POINT_QUOTA_EXCEEDED", "POINT_QUOTA_BLOCKED" -> decode {
                CreatePointOutcome.Blocked(Instant.parse(requireNotNull(error.detail("blockedUntil"))))
            }
            "INVALID_FIELD" -> AuthResult.Success(CreatePointOutcome.Invalid(
                when (error.detail("field")) {
                    "position", "lat", "lon" -> CreatePointField.Position
                    "topic", "presetKey", "customLabel" -> CreatePointField.Topic
                    "address", "addressSource" -> CreatePointField.Address
                    else -> null
                },
            ))
            "IDEMPOTENCY_KEY_REUSED" -> AuthResult.Success(CreatePointOutcome.KeyReused)
            else -> failure(response.status.toAuthError())
        }
    }

    private suspend fun <T> request(
        path: String,
        accessToken: String? = null,
        requestBody: String? = null,
        expected: Int,
        decodeBody: (String) -> T,
    ): AuthResult<T> {
        val response = execute(path, accessToken, requestBody) ?: return failure(AuthErrorCode.Network)
        if (response.status != expected) return failure(response.status.toAuthError())
        return decode { decodeBody(response.body) }
    }

    private suspend fun execute(
        path: String,
        accessToken: String?,
        body: String?,
        headers: Map<String, String> = emptyMap(),
    ) = transport.execute(
        AuthHttpRequest(
            method = if (body == null) AuthHttpMethod.Get else AuthHttpMethod.Post,
            url = "$origin/api/v1/$path",
            body = body,
            accessToken = accessToken,
            maxResponseBytes = 2 * 1024 * 1024,
            headers = headers,
        ),
    )
}

private inline fun <T> decode(block: () -> T): AuthResult<T> = try {
    AuthResult.Success(block())
} catch (_: IllegalArgumentException) {
    failure(AuthErrorCode.UnexpectedResponse)
}

private fun failure(code: AuthErrorCode) = AuthResult.Failure(AuthError(code))

private fun Int.toAuthError() = when (this) {
    401 -> AuthErrorCode.SessionExpired
    429 -> AuthErrorCode.RateLimited
    in 500..599 -> AuthErrorCode.ServiceUnavailable
    else -> AuthErrorCode.UnexpectedResponse
}

private fun AuthHttpResponse.error(): CreateErrorDto? = try {
    CreateJson.decodeFromString<CreateErrorEnvelopeDto>(body).error
} catch (_: IllegalArgumentException) {
    null
}

private val CreateJson = Json { ignoreUnknownKeys = true }
private val CreatePointId = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
private val CreateApiOrigin = Regex("""(?:https://[A-Za-z0-9.-]+|http://127\.0\.0\.1)(?::([0-9]{1,5}))?""")

@Serializable
private data class PresetListDto(val items: List<PresetDto>)

@Serializable
private data class PresetDto(val key: String, val label: String)

@Serializable
private data class LocationDto(val lat: Double, val lon: Double)

private fun Position.toDto() = LocationDto(latitude, longitude)

@Serializable
private data class ReverseRequestDto(val position: LocationDto)

@Serializable
private data class ReverseResponseDto(val address: String?, val source: String)

@Serializable
private data class CreatePointDto(
    val position: LocationDto,
    val topic: PointTopicInput,
    val address: String,
    val addressSource: String,
    val confirmNearby: Boolean,
)

@Serializable
private data class ContextDto(val nearby: List<NearbyDto>, val quota: CreationQuotaDto)

@Serializable
private data class NearbyDto(val id: String, val position: LocationDto, val address: String, val distanceMeters: Double) {
    fun toDomain() = NearbyPoint(id, Position(position.lat, position.lon), address, distanceMeters)
}

@Serializable
private data class CreationQuotaDto(
    val weekUsed: Int,
    val weekLimit: Int,
    val weekRemaining: Int,
    val weekNextAvailableAt: String?,
    val monthUsed: Int,
    val monthLimit: Int,
    val monthRemaining: Int,
    val monthNextAvailableAt: String?,
    val blockedUntil: String?,
    val lastAvailableWarning: Boolean,
) {
    fun toDomain() = PointCreationQuota(
        weekUsed, weekLimit, weekRemaining, weekNextAvailableAt?.let(Instant::parse),
        monthUsed, monthLimit, monthRemaining, monthNextAvailableAt?.let(Instant::parse),
        blockedUntil?.let(Instant::parse), lastAvailableWarning,
    )
}

@Serializable
private data class CreatedPointDto(val point: CreatedPointIdentityDto, val chat: CreatedChatDto, val quota: CreationQuotaDto)

@Serializable
private data class CreatedPointIdentityDto(val id: String)

@Serializable
private data class CreatedChatDto(val id: String, val pointId: String, val topic: CreatedTopicDto)

@Serializable
private data class CreatedTopicDto(val id: String, val label: String, val chatId: String)

@Serializable
private data class CreateErrorEnvelopeDto(val error: CreateErrorDto)

@Serializable
private data class CreateErrorDto(val code: String, val details: JsonObject? = null) {
    fun detail(name: String): String? = (details?.get(name) as? JsonPrimitive)?.contentOrNull
}
