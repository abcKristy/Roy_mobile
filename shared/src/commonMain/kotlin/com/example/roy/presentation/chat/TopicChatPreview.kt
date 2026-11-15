package com.example.roy.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.chat.ChatDetail
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.domain.chat.ChatMessageAuthor
import com.example.roy.presentation.theme.RoyTheme
import kotlin.time.Instant

@Preview
@Composable
private fun TopicChatLightPreview() {
    RoyTheme(darkTheme = false) { TopicChatView(previewState()) {} }
}

@Preview
@Composable
private fun TopicChatDarkPreview() {
    RoyTheme(darkTheme = true) { TopicChatView(previewState()) {} }
}

private fun previewState(): TopicChatViewState {
    val time = Instant.parse("2026-11-12T15:00:00Z")
    return TopicChatViewState(
        detail = ChatDetail("chat", "point", "topic", "Футбол", "Садовая улица, 12", "self", 1, 2),
        messages = listOf(
            ChatMessage("b", "chat", 2, "text", ChatMessageAuthor("other", "Ирина", null), "Увидимся у входа.", null, time),
            ChatMessage("a", "chat", 1, "system", null, null, "member_joined", time),
        ),
        isLoading = false,
    )
}
