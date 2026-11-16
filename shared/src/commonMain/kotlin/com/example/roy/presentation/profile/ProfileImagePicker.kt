package com.example.roy.presentation.profile

import androidx.compose.runtime.Composable
import com.example.roy.domain.profile.MaxProfileAvatarBytes
import com.example.roy.domain.profile.PickImageResult
import com.example.roy.domain.profile.ProfileProblem

@Composable
internal expect fun rememberProfileImagePicker(onResult: (PickImageResult) -> Unit): () -> Unit

internal fun pickedProfileImage(bytes: ByteArray): PickImageResult {
    if (bytes.size > MaxProfileAvatarBytes) return PickImageResult.Failure(ProfileProblem.TooLarge)
    val type = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[2] == 0xff.toByte() -> "image/jpeg"
        else -> return PickImageResult.Failure(ProfileProblem.InvalidImage)
    }
    return PickImageResult.Selected(bytes, type)
}
