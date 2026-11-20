package com.example.roy.presentation.chats

import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.chat.ChatListItem
import kotlin.time.Instant

internal data class ChatListViewState(
    val rows: List<ChatListItem> = emptyList(),
    val isLoading: Boolean = true,
    val nextCursor: String? = null,
    val error: AuthErrorCode? = null,
    val isOnline: Boolean = true,
    val cachedFetchedAt: Instant? = null,
)
