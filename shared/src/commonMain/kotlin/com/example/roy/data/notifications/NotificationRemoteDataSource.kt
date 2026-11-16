package com.example.roy.data.notifications

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthJson
import com.example.roy.data.network.encodeQueryValue
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.notifications.NotificationEntry
import com.example.roy.domain.notifications.NotificationPage
import com.example.roy.domain.notifications.NotificationRepository
import com.example.roy.domain.notifications.NotificationSettings
import com.example.roy.domain.notifications.NotificationSettingsWrite
import com.example.roy.domain.notifications.NotificationTarget
import com.example.roy.domain.notifications.notificationId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Instant

internal class NotificationRemoteDataSource(baseUrl: String, private val transport: AuthHttpTransport) : NotificationRepository {
    private val root = baseUrl.trimEnd('/') + "/api/v1/me"

    override suspend fun settings(token: String): AuthResult<NotificationSettings> =
        request("notification-settings", token) { decodeSettings(it) }

    override suspend fun update(value: NotificationSettings, token: String): AuthResult<NotificationSettingsWrite> =
        request(
            "notification-settings", token, AuthHttpMethod.Put,
            AuthJson.encodeToString(SettingsDto.from(value)),
            onError = { status, error ->
                if (status == 409 && error?.code == "STALE_STATE") {
                    NotificationSettingsWrite.Conflict(error.details?.let { details ->
                        parse { AuthJson.decodeFromJsonElement<SettingsDto>(details).domain() }
                    })
                } else null
            },
        ) { NotificationSettingsWrite.Saved(decodeSettings(it)) }

    override suspend fun list(cursor: String?, token: String): AuthResult<NotificationPage> =
        request("notifications?limit=30" + cursor?.let { "&cursor=${it.encodeQueryValue()}" }.orEmpty(), token) { body ->
            val page = AuthJson.decodeFromString<PageDto>(body)
            require(page.nextCursor == null || page.nextCursor.isNotEmpty() && page.nextCursor != cursor)
            NotificationPage(page.items.map(EntryDto::domain), page.nextCursor)
        }

    override suspend fun markRead(id: String, token: String): AuthResult<NotificationEntry> {
        if (id.notificationId() != id) return failure(AuthErrorCode.InvalidField)
        return request("notifications/$id/read", token, AuthHttpMethod.Put) { body ->
            AuthJson.decodeFromString<EntryDto>(body).domain().also { require(it.id == id && it.readAt != null) }
        }
    }

    override suspend fun setLocation(latitude: Double, longitude: Double, token: String): AuthResult<Unit> {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return failure(AuthErrorCode.InvalidField)
        return request(
            "notification-location", token, AuthHttpMethod.Put,
            AuthJson.encodeToString(LocationDto(PositionDto(latitude, longitude))),
        ) { Unit }
    }

    override suspend fun removeLocation(token: String): AuthResult<Unit> =
        request("notification-location", token, AuthHttpMethod.Delete, expectedStatus = 204) { Unit }

    private suspend fun <T : Any> request(
        path: String,
        token: String,
        method: AuthHttpMethod = AuthHttpMethod.Get,
        body: String? = null,
        expectedStatus: Int = 200,
        onError: (Int, ErrorDto?) -> T? = { _, _ -> null },
        decode: (String) -> T,
    ): AuthResult<T> {
        val response = transport.execute(AuthHttpRequest(method, "$root/$path", body, token, 512 * 1024))
            ?: return failure(AuthErrorCode.Network)
        if (response.status == expectedStatus) {
            return parse { decode(response.body) }?.let { AuthResult.Success(it) }
                ?: failure(AuthErrorCode.UnexpectedResponse)
        }
        val error = parse { AuthJson.decodeFromString<ErrorEnvelope>(response.body).error }
        onError(response.status, error)?.let { return AuthResult.Success(it) }
        return failure(when {
            response.status == 401 && error?.code == "AUTH_REQUIRED" -> AuthErrorCode.AuthRequired
            response.status == 401 -> AuthErrorCode.SessionExpired
            response.status == 403 && error?.code == "POLICY_NOT_ACCEPTED" -> AuthErrorCode.PolicyAcceptanceRequired
            response.status == 429 -> AuthErrorCode.RateLimited
            response.status in 500..599 -> AuthErrorCode.ServiceUnavailable
            response.status == 400 || response.status == 422 -> AuthErrorCode.InvalidField
            else -> AuthErrorCode.UnexpectedResponse
        })
    }
}

private fun decodeSettings(body: String) = AuthJson.decodeFromString<SettingsDto>(body).domain()

private inline fun <T> parse(block: () -> T): T? = try {
    block()
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun failure(code: AuthErrorCode) = AuthResult.Failure(AuthError(code))

@Serializable
private data class SettingsDto(
    val remindersPushEnabled: Boolean,
    val personalPushEnabled: Boolean,
    val extraPushEnabled: Boolean,
    val extraInAppEnabled: Boolean,
    val revision: Long? = null,
    val expectedRevision: Long? = null,
) {
    fun domain() = NotificationSettings(
        remindersPushEnabled, personalPushEnabled, extraPushEnabled, extraInAppEnabled,
        requireNotNull(revision).also { require(it >= 0) },
    )

    companion object {
        fun from(value: NotificationSettings) = SettingsDto(
            value.remindersPushEnabled, value.personalPushEnabled, value.extraPushEnabled, value.extraInAppEnabled,
            expectedRevision = value.revision,
        )
    }
}

@Serializable private data class PageDto(val items: List<EntryDto>, val nextCursor: String?)

@Serializable
private data class EntryDto(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val target: TargetDto,
    val createdAt: String,
    val readAt: String?,
) {
    fun domain() = NotificationEntry(
        requireNotNull(id.notificationId()), kind, title, body,
        NotificationTarget.parse(kind, id, target.pointId, target.chatId, target.proposalId),
        Instant.parse(createdAt), readAt?.let(Instant::parse),
    )
}

@Serializable private data class TargetDto(val pointId: String?, val chatId: String?, val proposalId: String?)
@Serializable private data class ErrorEnvelope(val error: ErrorDto)
@Serializable private data class ErrorDto(val code: String, val details: JsonElement? = null)
@Serializable private data class LocationDto(val position: PositionDto)
@Serializable private data class PositionDto(val lat: Double, val lon: Double)
