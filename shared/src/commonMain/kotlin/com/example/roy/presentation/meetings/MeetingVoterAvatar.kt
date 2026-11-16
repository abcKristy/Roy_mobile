package com.example.roy.presentation.meetings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.roy.domain.meetings.MeetingVote
import com.example.roy.domain.meetings.MeetingVoterAvatarLoader
import com.example.roy.presentation.theme.RoyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
internal fun MeetingVoterAvatar(voter: MeetingVote, loader: MeetingVoterAvatarLoader? = null) {
    val userId = voter.userId
    val path = voter.avatarUrl
    var bitmap by remember(loader, userId, path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(loader, userId, path) {
        if (loader == null || userId == null || path == null) return@LaunchedEffect
        val bytes = loader.load(userId, path) ?: return@LaunchedEffect
        bitmap = try {
            withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            null
        }
    }
    val initials = voter.displayName.takeIf { userId != null }
        ?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val shape = CircleShape
    val image = bitmap
    val size = RoyTheme.dimensions.avatarSmall
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier.size(size).background(RoyTheme.colors.secondary, shape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(initials, style = RoyTheme.typography.caption.copy(color = RoyTheme.colors.textPrimary))
        }
    }
}
