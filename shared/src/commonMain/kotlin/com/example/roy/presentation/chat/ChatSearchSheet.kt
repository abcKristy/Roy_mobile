package com.example.roy.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.components.fields.RoyTextField
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.chat_search
import roy.shared.generated.resources.chat_search_close
import roy.shared.generated.resources.chat_search_empty
import roy.shared.generated.resources.chat_search_field
import roy.shared.generated.resources.chat_search_more
import roy.shared.generated.resources.chat_search_working

@Composable
internal fun ChatSearchSheet(
    state: TopicChatSearch,
    currentUserId: String?,
    eventHandler: (TopicChatViewEvent) -> Unit,
) {
    val dimensions = RoyTheme.dimensions
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    Dialog(
        onDismissRequest = { eventHandler(TopicChatViewEvent.OnCloseSearch) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .background(
                        colors.surfaceElevated,
                        RoundedCornerShape(topStart = dimensions.radii.dialog, topEnd = dimensions.radii.dialog),
                    )
                    .padding(dimensions.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
            ) {
                BasicText(
                    stringResource(Res.string.chat_search),
                    modifier = Modifier.semantics { heading() },
                    style = typography.headingMedium.copy(color = colors.textPrimary),
                )
                RoyTextField(
                    value = state.query,
                    label = stringResource(Res.string.chat_search_field),
                    onValueChange = { eventHandler(TopicChatViewEvent.OnSearchQueryChanged(it)) },
                    errorText = state.issue?.takeIf { it == TopicChatIssue.SearchQuery }?.let { stringResource(it.stringResource) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onImeAction = { eventHandler(TopicChatViewEvent.OnSearchSubmitted) },
                )
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
                ) {
                    items(state.results, key = ChatMessage::id) { message ->
                        TopicChatMessageCard(message, currentUserId, isReportEnabled = false, eventHandler = eventHandler)
                    }
                    item {
                        when {
                            state.isLoading -> LoadingState(LoadingMode.Inline, stringResource(Res.string.chat_search_working))
                            state.issue != null && state.issue != TopicChatIssue.SearchQuery -> ErrorState(
                                stringResource(state.issue.stringResource), true,
                                { eventHandler(if (state.nextCursor == null) TopicChatViewEvent.OnSearchSubmitted else TopicChatViewEvent.OnSearchMore) },
                            )
                            state.hasSearched && state.results.isEmpty() -> BasicText(
                                stringResource(Res.string.chat_search_empty),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = typography.body.copy(color = colors.textSecondary),
                            )
                            state.nextCursor != null -> TertiaryButton(
                                label = stringResource(Res.string.chat_search_more),
                                onClick = { eventHandler(TopicChatViewEvent.OnSearchMore) },
                            )
                        }
                    }
                }
                TertiaryButton(
                    label = stringResource(Res.string.chat_search_close),
                    onClick = { eventHandler(TopicChatViewEvent.OnCloseSearch) },
                )
            }
        }
    }
}
