package com.example.roy.domain.profile

import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthUser

internal interface ProfileRepository {
    suspend fun getProfile(token: String): AuthResult<ProfileResult<AuthUser>>
    suspend fun updateName(name: String, token: String): AuthResult<ProfileResult<AuthUser>>
    suspend fun uploadAvatar(image: PickImageResult.Selected, token: String): AuthResult<ProfileResult<String>>
    suspend fun readAvatar(userId: String, etag: String?, token: String): AuthResult<ProfileResult<ProfileAvatar>>
    suspend fun deleteAvatar(token: String): AuthResult<ProfileResult<Unit>>
    suspend fun changePassword(current: String, replacement: String, token: String): AuthResult<ProfileResult<Unit>>
    suspend fun deleteAccount(current: String, confirm: Boolean, token: String): AuthResult<ProfileResult<Unit>>
}

internal sealed interface ProfileResult<out T> {
    data class Value<T>(val value: T) : ProfileResult<T>
    data class Rejected(val problem: ProfileProblem) : ProfileResult<Nothing>
}

internal enum class ProfileProblem {
    InvalidName, InvalidCurrentPassword, InvalidNewPassword, InvalidImage, TooLarge, AccessDenied,
    Network, Unavailable, UnexpectedResponse, DeletionUncertain,
}

internal sealed interface ProfileAvatar {
    class Image(val bytes: ByteArray, val mimeType: String, val etag: String?) : ProfileAvatar
    data object Missing : ProfileAvatar
    data object Unchanged : ProfileAvatar
}

internal sealed interface PickImageResult {
    class Selected(val bytes: ByteArray, val mimeType: String) : PickImageResult
    data object Cancelled : PickImageResult
    data class Failure(val problem: ProfileProblem) : PickImageResult
}

internal const val MAX_PROFILE_AVATAR_BYTES = 5 * 1024 * 1024

internal fun PickImageResult.Selected.validationError(): ProfileProblem? = when {
    bytes.size > MAX_PROFILE_AVATAR_BYTES -> ProfileProblem.TooLarge
    bytes.isEmpty() || mimeType !in setOf("image/jpeg", "image/png") -> ProfileProblem.InvalidImage
    else -> null
}
