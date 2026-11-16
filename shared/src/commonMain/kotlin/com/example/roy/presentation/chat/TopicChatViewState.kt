package com.example.roy.presentation.chat

import androidx.compose.runtime.Immutable
import com.example.roy.domain.chat.ChatDetail
import com.example.roy.domain.chat.ChatMessage
import kotlin.time.Instant

@Immutable
internal data class TopicChatViewState(
    val detail: ChatDetail? = null,
    val messages: List<ChatMessage> = emptyList(),
    val nextBeforeSeq: Long? = null,
    val isLoading: Boolean = true,
    val isOnline: Boolean = true,
    val isAccessUncertain: Boolean = false,
    val accessCheck: Long = 0,
    val isLoadingOlder: Boolean = false,
    val historyIssue: TopicChatIssue? = null,
    val olderIssue: TopicChatIssue? = null,
    val search: TopicChatSearch? = null,
    val isLeaveDialogOpen: Boolean = false,
    val isLeaving: Boolean = false,
    val reportTarget: ChatReportTarget? = null,
    val composer: ChatComposerState = ChatComposerState(),
    val cachedFetchedAt: Instant? = null,
) {
    val canInteract: Boolean get() = detail != null && historyIssue == null && cachedFetchedAt == null &&
        !isLoading && !isLeaving && !isAccessUncertain
}

@Immutable
internal data class TopicChatSearch(
    val query: String = "",
    val results: List<ChatMessage> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val issue: TopicChatIssue? = null,
)

internal enum class TopicChatIssue { Network, Unavailable, NotMember, Gone, Session, SearchQuery }
