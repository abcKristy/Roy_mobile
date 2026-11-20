package com.example.roy.presentation.chats

import com.example.roy.data.offline.OfflineCache
import com.example.roy.data.offline.OfflineRecord
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.chat.ChatListItem
import com.example.roy.domain.chat.ChatListOperations
import com.example.roy.domain.chat.ChatListPage
import com.example.roy.domain.chat.ChatListRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {
    @Test
    fun retryAfterPaginationNetworkFailureRefreshesCachedListFromFirstPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = SessionTestRepository()
            val session = SessionCoordinator(auth, SessionTestStore().apply { saved = auth.first })
            session.restore()
            val scope = (session.state.value as SessionStatus.Active).scopeId
            val requests = mutableListOf<String?>()
            val row = ChatListItem(
                "chat", "point", "topic", "other", "Topic", "Address", null, null,
                Instant.parse("2026-11-16T12:00:00Z"), 0,
            )
            val repository = object : ChatListRepository {
                override suspend fun page(cursor: String?, token: String): AuthResult<ChatListPage> {
                    requests += cursor
                    return if (cursor != null) AuthResult.Failure(AuthError(AuthErrorCode.Network))
                    else AuthResult.Success(ChatListPage(listOf(row), "next"))
                }

                override suspend fun advance(chatId: String, sequence: Long, token: String) =
                    error("Unexpected read cursor update")
            }
            var snapshot: String? = null
            val cache = OfflineCache(
                OfflineRecord({ null }, {}, {}),
                OfflineRecord({ snapshot }, { snapshot = it }, { snapshot = null }),
            )
            val model = ChatListViewModel(scope, ChatListOperations(session, repository), cache, session)
            model.handleEvent(ChatListViewEvent.Refresh)
            runCurrent()
            assertEquals("next", model.viewState.value.nextCursor)

            model.handleEvent(ChatListViewEvent.LoadMore)
            runCurrent()
            assertEquals(listOf(row), model.viewState.value.rows)
            assertNotNull(model.viewState.value.cachedFetchedAt)
            assertEquals(AuthErrorCode.Network, model.viewState.value.error)
            assertNull(model.viewState.value.nextCursor)

            model.handleEvent(ChatListViewEvent.Retry)
            runCurrent()
            assertEquals(listOf(null, "next", null), requests)
            assertNull(model.viewState.value.cachedFetchedAt)
            assertNull(model.viewState.value.error)
            assertEquals("next", model.viewState.value.nextCursor)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
