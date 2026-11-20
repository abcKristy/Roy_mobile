package com.example.roy.data.profile

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthJson
import com.example.roy.data.auth.ErrorEnvelopeDto
import com.example.roy.data.auth.UserDto
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.profile.MAX_PROFILE_AVATAR_BYTES
import com.example.roy.domain.profile.PickImageResult
import com.example.roy.domain.profile.ProfileAvatar
import com.example.roy.domain.profile.ProfileProblem
import com.example.roy.domain.profile.ProfileRepository
import com.example.roy.domain.profile.ProfileResult
import com.example.roy.domain.profile.validationError
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

internal class ProfileRemoteDataSource(origin: String, private val transport: AuthHttpTransport) : ProfileRepository {
    private val baseUrl = origin.trimEnd('/') + "/api/v1"

    override suspend fun getProfile(token: String) =
        request(AuthHttpMethod.Get, "/me", token, parse = { response ->
            AuthJson.decodeFromString<UserDto>(response.body).toDomain()
        })

    override suspend fun updateName(name: String, token: String) =
        request(AuthHttpMethod.Patch, "/me", token, AuthJson.encodeToString(NameInput(name)), ProfileProblem.InvalidName) {
            AuthJson.decodeFromString<UserDto>(it.body).toDomain()
        }

    override suspend fun uploadAvatar(image: PickImageResult.Selected, token: String): AuthResult<ProfileResult<String>> {
        image.validationError()?.let { return rejected(it) }
        val boundary = "roy-" + Uuid.random()
        val header = ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"avatar\"\r\n" +
            "Content-Type: ${image.mimeType}\r\n\r\n").encodeToByteArray()
        val ending = "\r\n--$boundary--\r\n".encodeToByteArray()
        val body = ByteArray(header.size + image.bytes.size + ending.size)
        header.copyInto(body)
        image.bytes.copyInto(body, header.size)
        ending.copyInto(body, header.size + image.bytes.size)
        val response = transport.execute(AuthHttpRequest(
            AuthHttpMethod.Put, "$baseUrl/me/avatar", null, token,
            requestBytes = body, requestContentType = "multipart/form-data; boundary=$boundary",
        ))
        return decode(response, ProfileProblem.InvalidImage) {
            AuthJson.decodeFromString<AvatarOutput>(it.body).avatarUrl.takeIf { url -> url.isNotBlank() }
        }
    }

    override suspend fun readAvatar(userId: String, etag: String?, token: String): AuthResult<ProfileResult<ProfileAvatar>> {
        if (!userId.matches(UserId)) return rejected(ProfileProblem.AccessDenied)
        val headers = buildMap {
            put("Accept", "image/jpeg, image/png, application/json")
            etag?.takeIf { '\r' !in it && '\n' !in it }?.let { put("If-None-Match", it) }
        }
        val response = transport.execute(AuthHttpRequest(
            AuthHttpMethod.Get, "$baseUrl/users/$userId/avatar", null, token, MAX_PROFILE_AVATAR_BYTES, headers,
            isBinaryResponseExpected = true,
        ))
        if (response?.status == 304) return AuthResult.Success(ProfileResult.Value(ProfileAvatar.Unchanged))
        if (response?.status == 404) return AuthResult.Success(ProfileResult.Value(ProfileAvatar.Missing))
        return decode(response) { reply ->
            val bytes = reply.binaryBody ?: return@decode null
            val type = reply.contentType?.substringBefore(';') ?: return@decode null
            val image = PickImageResult.Selected(bytes, type)
            if (image.validationError() == null) ProfileAvatar.Image(bytes, type, reply.etag) else null
        }
    }

    override suspend fun deleteAvatar(token: String) =
        request(AuthHttpMethod.Delete, "/me/avatar", token) { Unit.takeIf { _ -> it.status == 204 } }

    override suspend fun changePassword(current: String, replacement: String, token: String) =
        request(
            AuthHttpMethod.Post, "/me/password", token, AuthJson.encodeToString(PasswordInput(current, replacement)),
            ProfileProblem.InvalidNewPassword,
        ) { Unit.takeIf { _ -> it.status == 204 } }

    override suspend fun deleteAccount(current: String, confirm: Boolean, token: String): AuthResult<ProfileResult<Unit>> {
        if (!confirm) return rejected(ProfileProblem.AccessDenied)
        val response = transport.execute(AuthHttpRequest(
            AuthHttpMethod.Post, "$baseUrl/me/deletion", AuthJson.encodeToString(DeletionInput(current, confirm)), token,
        ))
        if (response == null || response.status >= 500) return rejected(ProfileProblem.DeletionUncertain)
        return decode(response) { Unit.takeIf { _ -> it.status == 204 } }
    }

    private suspend fun <T : Any> request(
        method: AuthHttpMethod,
        path: String,
        token: String,
        body: String? = null,
        invalidField: ProfileProblem = ProfileProblem.UnexpectedResponse,
        parse: (AuthHttpResponse) -> T?,
    ): AuthResult<ProfileResult<T>> = decode(transport.execute(AuthHttpRequest(method, baseUrl + path, body, token)), invalidField, parse)

    private fun <T : Any> decode(
        response: AuthHttpResponse?,
        invalidField: ProfileProblem = ProfileProblem.UnexpectedResponse,
        parse: (AuthHttpResponse) -> T?,
    ): AuthResult<ProfileResult<T>> {
        if (response == null) return rejected(ProfileProblem.Network)
        if (response.status == 401) return AuthResult.Failure(AuthError(AuthErrorCode.SessionExpired))
        if (response.status in 200..299) {
            val value = runCatching { parse(response) }.getOrNull()
                ?: return rejected(ProfileProblem.UnexpectedResponse)
            return AuthResult.Success(ProfileResult.Value(value))
        }
        val code = runCatching { AuthJson.decodeFromString<ErrorEnvelopeDto>(response.body).error.code }.getOrNull()
        return rejected(when {
            code == "INVALID_CURRENT_PASSWORD" -> ProfileProblem.InvalidCurrentPassword
            response.status == 413 -> ProfileProblem.TooLarge
            response.status == 403 -> ProfileProblem.AccessDenied
            response.status == 422 || response.status == 415 -> invalidField
            response.status == 429 || response.status >= 500 -> ProfileProblem.Unavailable
            else -> ProfileProblem.UnexpectedResponse
        })
    }

    private fun rejected(problem: ProfileProblem) = AuthResult.Success(ProfileResult.Rejected(problem))

    private companion object {
        val UserId = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    }
}

@Serializable private class NameInput(val displayName: String)
@Serializable private class AvatarOutput(val avatarUrl: String)
@Serializable private class PasswordInput(val currentPassword: String, val newPassword: String)
@Serializable private class DeletionInput(val currentPassword: String, val confirm: Boolean)
