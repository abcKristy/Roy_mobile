package com.example.roy.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.theme.RoyTheme
import com.example.roy.presentation.core.formatLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.chat_author_deleted
import roy.shared.generated.resources.chat_message_report
import roy.shared.generated.resources.chat_system_booking_cancelled
import roy.shared.generated.resources.chat_system_default
import roy.shared.generated.resources.chat_system_meeting_booked
import roy.shared.generated.resources.chat_system_meeting_closed
import roy.shared.generated.resources.chat_system_meeting_proposed
import roy.shared.generated.resources.chat_system_meeting_ready
import roy.shared.generated.resources.chat_system_member_joined
import roy.shared.generated.resources.chat_system_member_left
import roy.shared.generated.resources.chat_system_point_deleted

@Composable
internal fun TopicChatMessageCard(
    message: ChatMessage,
    currentUserId: String?,
    isReportEnabled: Boolean,
    eventHandler: (TopicChatViewEvent) -> Unit,
) {
    val typography = RoyTheme.typography
    val colors = RoyTheme.colors
    val spacing = RoyTheme.dimensions.spacing
    if (message.type != "text") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            BasicText(
                stringResource(chatSystemMessageLabel(message.systemKind)),
                style = typography.bodySmall.copy(color = colors.textSecondary, textAlign = TextAlign.Center),
            )
            MessageTime(message)
        }
        return
    }

    val author = message.author
    val authorId = author?.id
    val own = authorId != null && authorId == currentUserId
    val label = if (authorId == null) stringResource(Res.string.chat_author_deleted) else requireNotNull(author).displayName
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (own) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        RoyCard(Modifier.fillMaxWidth(0.92f)) {
            BasicText(label, style = typography.bodySmall.copy(color = colors.textSecondary))
            BasicText(message.text.orEmpty(), style = typography.body.copy(color = colors.textPrimary))
            MessageTime(message)
            if (isReportEnabled && authorId != null && !own) {
                TertiaryButton(
                    label = stringResource(Res.string.chat_message_report),
                    onClick = { eventHandler(TopicChatViewEvent.OnReportRequested(message.id)) },
                )
            }
        }
    }
}

@Composable
private fun MessageTime(message: ChatMessage) {
    BasicText(
        formatLocalDateTime(message.createdAt),
        style = RoyTheme.typography.caption.copy(color = RoyTheme.colors.textSecondary),
    )
}

internal fun chatSystemMessageLabel(kind: String?): StringResource = when (kind) {
    "member_joined" -> Res.string.chat_system_member_joined
    "member_left" -> Res.string.chat_system_member_left
    "meeting_proposed" -> Res.string.chat_system_meeting_proposed
    "meeting_ready" -> Res.string.chat_system_meeting_ready
    "meeting_booked" -> Res.string.chat_system_meeting_booked
    "meeting_booking_cancelled" -> Res.string.chat_system_booking_cancelled
    "meeting_closed" -> Res.string.chat_system_meeting_closed
    "point_deleted" -> Res.string.chat_system_point_deleted
    else -> Res.string.chat_system_default
}
