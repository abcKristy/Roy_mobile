package com.example.roy.presentation.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.chat.ChatListItem
import com.example.roy.presentation.theme.RoyTheme
import kotlin.time.Instant

@Preview(name = "Мои чаты — светлая тема")
@Composable
private fun ChatListLightPreview() = ChatListPreview(false)

@Preview(name = "Мои чаты — тёмная тема")
@Composable
private fun ChatListDarkPreview() = ChatListPreview(true)

@Composable
private fun ChatListPreview(darkTheme: Boolean) {
    val timestamp = Instant.parse("2026-11-15T11:00:00Z")
    val row = ChatListItem(
        "chat", "point", "topic", "football", "Футбол", "Тестовый сквер, 1",
        "Участник присоединился", timestamp, timestamp, 102,
    )
    RoyTheme(darkTheme = darkTheme) {
        ChatListView(ChatListViewState(listOf(row), isLoading = false), eventHandler = {})
    }
}
