package com.example.roy.presentation.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.roy.domain.profile.MAX_PROFILE_AVATAR_BYTES
import com.example.roy.domain.profile.PickImageResult
import com.example.roy.domain.profile.ProfileProblem
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberProfileImagePicker(onResult: (PickImageResult) -> Unit): () -> Unit {
    val currentResult = rememberUpdatedState(onResult)
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            currentResult.value(PickImageResult.Cancelled)
        } else scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    resolver.openInputStream(uri)?.use { source ->
                        val output = ByteArrayOutputStream()
                        val chunk = ByteArray(16 * 1024)
                        while (true) {
                            val count = source.read(chunk)
                            if (count <= 0) break
                            if (output.size() + count > MAX_PROFILE_AVATAR_BYTES) {
                                return@use PickImageResult.Failure(ProfileProblem.TooLarge)
                            }
                            output.write(chunk, 0, count)
                        }
                        pickedProfileImage(output.toByteArray())
                    } ?: PickImageResult.Failure(ProfileProblem.InvalidImage)
                } catch (_: IOException) {
                    PickImageResult.Failure(ProfileProblem.InvalidImage)
                } catch (_: SecurityException) {
                    PickImageResult.Failure(ProfileProblem.InvalidImage)
                }
            }
            currentResult.value(result)
        }
    }
    return {
        try {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: IllegalStateException) {
            currentResult.value(PickImageResult.Failure(ProfileProblem.Unavailable))
        } catch (_: android.content.ActivityNotFoundException) {
            currentResult.value(PickImageResult.Failure(ProfileProblem.Unavailable))
        }
    }
}
