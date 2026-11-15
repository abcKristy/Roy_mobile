package com.example.roy.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.core.formatLocalDateTime
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.chat_composer
import roy.shared.generated.resources.chat_flood
import roy.shared.generated.resources.chat_invalid_text
import roy.shared.generated.resources.chat_mute
import roy.shared.generated.resources.chat_reject_pattern
import roy.shared.generated.resources.chat_reject_phone
import roy.shared.generated.resources.chat_reject_profanity
import roy.shared.generated.resources.chat_reject_url
import roy.shared.generated.resources.chat_resend
import roy.shared.generated.resources.chat_send
import roy.shared.generated.resources.chat_send_failed
import roy.shared.generated.resources.chat_send_uncertain
import roy.shared.generated.resources.point_detail_network

@Composable
internal fun TopicChatComposer(
    state: ChatComposerState,
    isEnabled: Boolean,
    isOnline: Boolean,
    eventHandler: (TopicChatViewEvent) -> Unit,
) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val dimensions = RoyTheme.dimensions
    val spacing = dimensions.spacing
    val shape = RoundedCornerShape(dimensions.radii.textField)
    val label = stringResource(Res.string.chat_composer)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        if (!isOnline) ComposerNotice(stringResource(Res.string.point_detail_network), isError = true)
        state.mutedUntil?.let {
            ComposerNotice(stringResource(Res.string.chat_mute, formatLocalDateTime(it)), isError = false)
        }
        if (state.retryAfterSeconds > 0) {
            ComposerNotice(stringResource(Res.string.chat_flood, state.retryAfterSeconds), isError = false)
        }
        state.issue?.let {
            ComposerNotice(
                stringResource(when (it) {
                    ChatComposerIssue.Uncertain -> Res.string.chat_send_uncertain
                    ChatComposerIssue.RejectedPhone -> Res.string.chat_reject_phone
                    ChatComposerIssue.RejectedUrl -> Res.string.chat_reject_url
                    ChatComposerIssue.RejectedProfanity -> Res.string.chat_reject_profanity
                    ChatComposerIssue.RejectedPattern -> Res.string.chat_reject_pattern
                    ChatComposerIssue.InvalidText -> Res.string.chat_invalid_text
                    ChatComposerIssue.SendFailed -> Res.string.chat_send_failed
                }),
                isError = true,
            )
        }
        BasicTextField(
            value = state.text,
            onValueChange = { eventHandler(TopicChatViewEvent.OnComposerEdited(it)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.minimumTouchTarget)
                .background(colors.surfaceElevated, shape)
                .border(androidx.compose.ui.unit.Dp.Hairline, colors.outline, shape)
                .padding(horizontal = spacing.medium, vertical = spacing.compact)
                .semantics { contentDescription = label },
            enabled = isEnabled,
            readOnly = state.isSending,
            textStyle = typography.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            maxLines = 4,
            decorationBox = { innerTextField ->
                if (state.text.isEmpty()) BasicText(label, style = typography.body.copy(color = colors.textSecondary))
                innerTextField()
            },
        )
        PrimaryButton(
            text = stringResource(if (state.issue == ChatComposerIssue.Uncertain) Res.string.chat_resend else Res.string.chat_send),
            onClick = { eventHandler(TopicChatViewEvent.OnSend) },
            modifier = Modifier.fillMaxWidth(),
            isEnabled = isEnabled && isOnline && state.canSend,
            isSubmitting = state.isSending,
        )
    }
}

@Composable
private fun ComposerNotice(text: String, isError: Boolean) {
    val colors = RoyTheme.colors
    BasicText(
        text,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = RoyTheme.typography.bodySmall.copy(color = if (isError) colors.error else colors.textPrimary),
    )
}
