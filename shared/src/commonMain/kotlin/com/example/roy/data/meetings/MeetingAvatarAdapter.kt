package com.example.roy.data.meetings

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.meetings.MeetingVoterAvatarLoader

internal class MeetingAvatarAdapter(
    baseUrl: String,
    private val transport: AuthHttpTransport,
    private val sessions: SessionCoordinator,
) {
    private val origin = baseUrl.trimEnd('/').takeIf { value ->
        val match = ORIGIN.matchEntire(value)
        val port = match?.groupValues?.get(1).orEmpty()
        match != null && (port.isEmpty() || port.toIntOrNull()?.let { it in 1..65535 } == true)
    }

    fun forScope(scopeId: Long): MeetingVoterAvatarLoader = MeetingVoterAvatarLoader { userId, avatarPath ->
        val apiOrigin = origin ?: return@MeetingVoterAvatarLoader null
        if (!USER_ID.matches(userId) || avatarPath != "/api/v1/users/$userId/avatar") {
            return@MeetingVoterAvatarLoader null
        }

        when (val result = sessions.readProtected(scopeId) { accessToken -> read("$apiOrigin$avatarPath", accessToken) }) {
            is AuthResult.Success -> result.value
            is AuthResult.Failure -> null
        }
    }

    private suspend fun read(url: String, accessToken: String): AuthResult<ByteArray?> {
        val response = transport.execute(
            AuthHttpRequest(
                method = AuthHttpMethod.Get,
                url = url,
                body = null,
                accessToken = accessToken,
                maxResponseBytes = MAX_AVATAR_BYTES,
                headers = mapOf("Accept" to "image/jpeg, image/png"),
                isBinaryResponseExpected = true,
            ),
        ) ?: return AuthResult.Failure(AuthError(AuthErrorCode.Network))
        if (response.status == 401) return AuthResult.Failure(AuthError(AuthErrorCode.AuthRequired))
        val type = response.contentType?.substringBefore(';')?.trim()?.lowercase()
        return AuthResult.Success(
            response.binaryBody.takeIf { response.status == 200 && type != null && type in CONTENT_TYPES }
                ?.takeIf { it.size in 1..MAX_AVATAR_BYTES },
        )
    }

    private companion object {
        const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
        val CONTENT_TYPES = setOf("image/jpeg", "image/png")
        val USER_ID = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        val ORIGIN = Regex("""(?:https://[A-Za-z0-9.-]+|http://(?:127\.0\.0\.1|localhost))(?::([0-9]{1,5}))?""")
    }
}
