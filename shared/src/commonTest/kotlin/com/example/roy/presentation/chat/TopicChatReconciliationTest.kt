package com.example.roy.presentation.chat

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.chat.ChatDetail
import com.example.roy.domain.chat.ChatHistoryPage
import com.example.roy.domain.chat.ChatHistoryRepository
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.domain.chat.ChatReadCursor
import com.example.roy.domain.chat.ChatReadReply
import com.example.roy.domain.chat.ChatRealtimeEvent
import com.example.roy.domain.chat.ChatWriteRepository
import com.example.roy.domain.points.PointDetailGateway
import com.example.roy.domain.points.PointTopicInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TopicChatReconciliationTest {
    @Test
    fun eventBetweenHistorySnapshotAndResponseIsMergedOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val model = fixture.model()
        try {
            model.handleEvent(TopicChatViewEvent.OnResume)
            runCurrent()
            fixture.events.emit(ChatRealtimeEvent.MessageCreated(message(2)))
            runCurrent()
            assertTrue(model.viewState.value.messages.isEmpty())

            fixture.historyGate.complete(Unit)
            runCurrent()
            assertEquals(listOf(2L, 1L), model.viewState.value.messages.map(ChatMessage::messageSeq))
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun delayedMembershipEventHidesDataUntilFreshDetailConfirmsAccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val model = fixture.model()
        try {
            fixture.historyGate.complete(Unit)
            model.handleEvent(TopicChatViewEvent.OnResume)
            runCurrent()
            assertTrue(model.viewState.value.canInteract)

            val verification = CompletableDeferred<Unit>()
            fixture.detailGate = verification
            fixture.events.emit(ChatRealtimeEvent.AccessLost("chat"))
            runCurrent()
            assertTrue(model.viewState.value.isAccessUncertain)
            assertFalse(model.viewState.value.canInteract)

            verification.complete(Unit)
            runCurrent()
            assertFalse(model.viewState.value.isAccessUncertain)
            assertTrue(model.viewState.value.canInteract)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun visibleReadRetriesTransientFailureWithoutAnotherVisibilityEvent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture().apply { historyGate.complete(Unit) }
        var attempts = 0
        val model = fixture.model(ChatReadCursor { sequence ->
            if (++attempts == 1) AuthResult.Failure(AuthError(AuthErrorCode.Network))
            else AuthResult.Success(sequence)
        })
        try {
            model.handleEvent(TopicChatViewEvent.OnResume)
            runCurrent()
            model.handleEvent(TopicChatViewEvent.OnVisibleMessage(1L))
            advanceTimeBy(251)
            assertEquals(1, attempts)
            advanceTimeBy(1_000)
            assertEquals(2, attempts)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun reportCannotSubmitOfflineAndDiscardClearsDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = SessionTestRepository()
        val session = SessionCoordinator(auth, SessionTestStore().apply { saved = auth.first })
        session.restore()
        val scope = (session.state.value as SessionStatus.Active).scopeId
        val connectivity = MutableStateFlow(true)
        val model = ChatReportViewModel(ChatReportTarget("author", "Автор"), "chat", scope, session, UnusedWriter, connectivity)
        try {
            model.handleEvent(ChatReportEvent.Edit("Причина жалобы"))
            connectivity.value = false
            runCurrent()
            model.handleEvent(ChatReportEvent.Submit)
            runCurrent()
            assertFalse(model.viewState.value.canSubmit)
            assertFalse(model.viewState.value.isSubmitting)
            model.discard()
            assertEquals("", model.viewState.value.reason)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class Fixture {
        val events = MutableSharedFlow<ChatRealtimeEvent>()
        val historyGate = CompletableDeferred<Unit>()
        var detailGate: CompletableDeferred<Unit>? = null

        private val repository = object : ChatHistoryRepository {
            override suspend fun detail(chatId: String, token: String): AuthResult<ChatReadReply<ChatDetail>> {
                detailGate?.await()
                return AuthResult.Success(ChatReadReply.Data(ChatDetail(chatId, "point", "topic", "Бег", "Адрес", "fixture-user", 0, 1)))
            }

            override suspend fun history(chatId: String, token: String, beforeSeq: Long?, afterSeq: Long?) =
                AuthResult.Success(ChatReadReply.Data(
                    if (afterSeq == null) {
                        historyGate.await()
                        ChatHistoryPage(listOf(message(1)), null, null)
                    } else ChatHistoryPage(listOf(message(2)), null, null)
                ))

            override suspend fun search(chatId: String, token: String, query: String, cursor: String?) =
                error("Unexpected search")
        }

        suspend fun model(unread: ChatReadCursor = ChatReadCursor { AuthResult.Success(it) }): TopicChatViewModel {
            val auth = SessionTestRepository()
            val session = SessionCoordinator(auth, SessionTestStore().apply { saved = auth.first })
            session.restore()
            val scope = (session.state.value as SessionStatus.Active).scopeId
            return TopicChatViewModel(
                scope, "point", "topic", "chat", session, repository, UnusedPointDetails,
                unread, UnusedWriter, { events }, flowOf(true), {},
            )
        }
    }

    private object UnusedPointDetails : PointDetailGateway {
        override suspend fun point(pointId: String) = error("Unexpected point")
        override suspend fun memberships(pointId: String, scopeId: Long, chatIds: Set<String>) = error("Unexpected memberships")
        override suspend fun presets() = error("Unexpected presets")
        override suspend fun add(pointId: String, scopeId: Long, topic: PointTopicInput, key: String) = error("Unexpected add")
        override suspend fun join(chatId: String, scopeId: Long) = error("Unexpected join")
        override suspend fun leave(chatId: String, scopeId: Long) = error("Unexpected leave")
    }

    private object UnusedWriter : ChatWriteRepository {
        override suspend fun send(chatId: String, text: String, key: String, token: String) = error("Unexpected send")
        override suspend fun report(chatId: String, targetUserId: String, reason: String, key: String, token: String) =
            error("Unexpected report")
    }

    companion object {
        private fun message(sequence: Long) = ChatMessage(
            "message-" + sequence, "chat", sequence, "text", null, "Сообщение", null,
            Instant.parse("2026-11-15T10:00:00Z"),
        )
    }
}
