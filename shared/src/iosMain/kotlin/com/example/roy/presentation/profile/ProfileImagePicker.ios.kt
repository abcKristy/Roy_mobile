package com.example.roy.presentation.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import com.example.roy.domain.profile.MAX_PROFILE_AVATAR_BYTES
import com.example.roy.domain.profile.PickImageResult
import com.example.roy.domain.profile.ProfileProblem
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSProgress
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
internal actual fun rememberProfileImagePicker(onResult: (PickImageResult) -> Unit): () -> Unit {
    val controller = LocalUIViewController.current
    val callback = rememberUpdatedState(onResult)
    val delegate = remember { ProfilePickerDelegate { callback.value(it) } }
    DisposableEffect(delegate) { onDispose { delegate.dispose() } }
    return {
        if (controller.presentedViewController != null) {
            callback.value(PickImageResult.Failure(ProfileProblem.Unavailable))
        } else {
            val configuration = PHPickerConfiguration().apply {
                filter = PHPickerFilter.imagesFilter
                selectionLimit = 1
            }
            val picker = PHPickerViewController(configuration).apply { this.delegate = delegate }
            controller.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class ProfilePickerDelegate(private val result: (PickImageResult) -> Unit) :
    NSObject(), PHPickerViewControllerDelegateProtocol {
    private var isActive = true
    private var progress: NSProgress? = null

    fun dispose() {
        isActive = false
        progress?.cancel()
        progress = null
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            if (isActive) result(PickImageResult.Cancelled)
            return
        }
        val type = listOf("public.png", "public.jpeg").firstOrNull { provider.hasItemConformingToTypeIdentifier(it) }
        if (type == null) {
            if (isActive) result(PickImageResult.Failure(ProfileProblem.InvalidImage))
            return
        }
        progress = provider.loadFileRepresentationForTypeIdentifier(type) { url, _ ->
            val selected = url?.path?.let(::readImage) ?: PickImageResult.Failure(ProfileProblem.InvalidImage)
            dispatch_async(dispatch_get_main_queue()) {
                progress = null
                if (isActive) result(selected)
            }
        }
    }

    private fun readImage(path: String): PickImageResult {
        val input = fopen(path, "rb") ?: return PickImageResult.Failure(ProfileProblem.InvalidImage)
        try {
            val bytes = ByteArray(MAX_PROFILE_AVATAR_BYTES + 1)
            val count = bytes.usePinned { fread(it.addressOf(0), 1UL, bytes.size.toULong(), input).toInt() }
            if (ferror(input) != 0) return PickImageResult.Failure(ProfileProblem.InvalidImage)
            return if (count > MAX_PROFILE_AVATAR_BYTES) PickImageResult.Failure(ProfileProblem.TooLarge)
            else pickedProfileImage(bytes.copyOf(count))
        } finally {
            fclose(input)
        }
    }
}
