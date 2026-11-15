package com.example.roy.presentation.chat

import com.example.roy.domain.auth.AuthCredentials
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.chat.ChatReportReply
import com.example.roy.domain.chat.ChatWriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChatReportViewModelTest {
    @Test
    fun cancelDoesNotPostAndExplicitRetryUsesTheOriginalCommand() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val writer = ReportWriter()
            val model = model(writer)
            model.handleEvent(ChatReportEvent.Edit("Нарушение правил"))
            model.handleEvent(ChatReportEvent.Close)
            model.handleEvent(ChatReportEvent.Submit)
            runCurrent()
            assertEquals(emptyList(), writer.requests)

            model.handleEvent(ChatReportEvent.Edit("  Нарушение правил  "))
            model.handleEvent(ChatReportEvent.Submit)
            model.handleEvent(ChatReportEvent.Submit)
            runCurrent()
            assertEquals(ChatReportFeedback.Retry, model.viewState.value.feedback)
            assertEquals(1, writer.requests.size)

            model.handleEvent(ChatReportEvent.Submit)
            runCurrent()
            assertEquals(writer.requests[0], writer.requests[1])
            assertEquals("Нарушение правил", writer.requests[0].first)
            assertEquals(ChatReportFeedback.Duplicate, model.viewState.value.feedback)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun changedReasonUsesANewIdempotencyKey() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val writer = ReportWriter()
            val model = model(writer)
            model.handleEvent(ChatReportEvent.Edit("Первая причина"))
            model.handleEvent(ChatReportEvent.Submit)
            runCurrent()
            model.handleEvent(ChatReportEvent.Edit("Другая причина"))
            model.handleEvent(ChatReportEvent.Submit)
            runCurrent()
            assertNotEquals(writer.requests[0].second, writer.requests[1].second)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun model(writer: ReportWriter): ChatReportViewModel {
        val session = SessionCoordinator(SessionTestRepository(), SessionTestStore())
        session.restore()
        session.authenticate(AuthCredentials.Login("+123", "fixture"))
        val id = (session.state.value as SessionStatus.Active).scopeId
        return ChatReportViewModel(ChatReportTarget("peer", "Собеседник"), "chat", id, session, writer)
    }

    private class ReportWriter : ChatWriteRepository {
        val requests = mutableListOf<Pair<String, String>>()

        override suspend fun send(chatId: String, text: String, key: String, token: String) =
            error("Unexpected send")

        override suspend fun report(
            chatId: String,
            targetUserId: String,
            reason: String,
            key: String,
            token: String,
        ): AuthResult<ChatReportReply> {
            requests += reason to key
            return if (requests.size == 1) AuthResult.Failure(AuthError(AuthErrorCode.Network))
            else AuthResult.Success(ChatReportReply.Duplicate)
        }
    }
}
