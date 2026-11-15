package com.example.roy.data.points

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthJson
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.map.Position
import com.example.roy.domain.navigation.DraftContext
import com.example.roy.domain.navigation.ProtectedTargetRepository
import com.example.roy.domain.navigation.ProtectedTargetResult
import kotlinx.serialization.Serializable

internal class ProtectedTargetRemoteDataSource(
    baseUrl: String,
    private val transport: AuthHttpTransport,
) : ProtectedTargetRepository {
    private val origin = baseUrl.trimEnd('/')

    override suspend fun draft(position: Position, accessToken: String) =
        read("points/creation-context?lat=${position.latitude}&lon=${position.longitude}", accessToken) { body ->
            val context = AuthJson.decodeFromString<CreationContextDto>(body)
            DraftContext(
                context.quota.weekRemaining,
                context.quota.monthRemaining,
                context.nearby.map { it.address },
            )
        }

    override suspend fun chatAccess(chatId: String, accessToken: String): AuthResult<ProtectedTargetResult<Unit>> {
        if (!ChatId.matches(chatId)) return AuthResult.Success(ProtectedTargetResult.Gone)
        return read("chats/$chatId", accessToken) { body ->
            val detail = AuthJson.decodeFromString<ChatAccessDto>(body)
            require(detail.id == chatId && detail.membership.chatId == chatId)
        }
    }

    private suspend fun <T> read(
        path: String,
        accessToken: String,
        decode: (String) -> T,
    ): AuthResult<ProtectedTargetResult<T>> {
        val response = transport.execute(AuthHttpRequest(AuthHttpMethod.Get, "$origin/api/v1/$path", null, accessToken))
            ?: return AuthResult.Failure(AuthError(AuthErrorCode.Network))
        return when (response.status) {
            200 -> try {
                AuthResult.Success(ProtectedTargetResult.Ready(decode(response.body)))
            } catch (_: IllegalArgumentException) {
                AuthResult.Failure(AuthError(AuthErrorCode.UnexpectedResponse))
            }
            401 -> AuthResult.Failure(AuthError(AuthErrorCode.AuthRequired))
            403 -> AuthResult.Success(ProtectedTargetResult.Forbidden)
            404 -> AuthResult.Success(ProtectedTargetResult.Gone)
            else -> AuthResult.Success(ProtectedTargetResult.Unavailable)
        }
    }

    private companion object {
        val ChatId = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}

@Serializable
private data class CreationContextDto(val nearby: List<NearbyPointDto>, val quota: QuotaDto)

@Serializable
private data class NearbyPointDto(val address: String)

@Serializable
private data class QuotaDto(val weekRemaining: Int, val monthRemaining: Int)

@Serializable
private data class ChatAccessDto(val id: String, val membership: ChatMembershipDto)

@Serializable
private data class ChatMembershipDto(val chatId: String)
