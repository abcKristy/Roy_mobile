package com.example.roy.presentation.chat

import com.example.roy.domain.auth.AuthCredentials
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthUser
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.domain.chat.ChatReportReply
import com.example.roy.domain.chat.ChatSendReply
import com.example.roy.domain.chat.ChatWriteRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ChatComposerControllerTest {
    @Test
    fun uncertainWriteCanOnlyBeRepeatedExplicitlyWithOriginalTextAndKey() = runTest {
        val fixture = composerFixture()
        val pending = CompletableDeferred<AuthResult<ChatSendReply>>()
        fixture.writer.reply = { pending.await() }
        fixture.controller.edit("Встречаемся")
        fixture.controller.send()
        fixture.controller.send()
        runCurrent()
        assertEquals(listOf("Встречаемся" to "key-1"), fixture.writer.attempts)
        assertTrue(fixture.controller.state.value.isSending)

        pending.complete(AuthResult.Failure(AuthError(AuthErrorCode.Network)))
        runCurrent()
        assertEquals(ChatComposerIssue.Uncertain, fixture.controller.state.value.issue)
        fixture.controller.syncProfile()
        advanceTimeBy(4.seconds)
        assertEquals(1, fixture.writer.attempts.size)

        fixture.writer.reply = { AuthResult.Success(ChatSendReply.Sent(acceptedMessage)) }
        fixture.controller.send()
        runCurrent()
        assertEquals(List(2) { "Встречаемся" to "key-1" }, fixture.writer.attempts)
        assertEquals(listOf(acceptedMessage), fixture.sent)
        assertEquals("", fixture.controller.state.value.text)
    }

    @Test
    fun floodDeadlineUsesServerDurationAndRequiresNewExplicitSend() = runTest {
        val fixture = composerFixture()
        fixture.writer.reply = { AuthResult.Success(ChatSendReply.Flood(2)) }
        fixture.controller.edit("Иду")
        fixture.controller.send()
        runCurrent()
        assertEquals(2, fixture.controller.state.value.retryAfterSeconds)
        assertFalse(fixture.controller.state.value.canSend)
        fixture.controller.send()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(1, fixture.writer.attempts.size)
        assertEquals("Иду", fixture.controller.state.value.text)
        assertTrue(fixture.controller.state.value.canSend)

        fixture.writer.reply = { AuthResult.Success(ChatSendReply.Rejected("phone")) }
        fixture.controller.send()
        runCurrent()
        assertEquals(listOf("Иду" to "key-1", "Иду" to "key-2"), fixture.writer.attempts)
        assertEquals(ChatComposerIssue.RejectedPhone, fixture.controller.state.value.issue)
        assertEquals(emptyList(), fixture.sent)
    }

    @Test
    fun editingWhileAwaitingResponsePreservesNewDraftEvenAtSameText() = runTest {
        val fixture = composerFixture()
        val pending = CompletableDeferred<AuthResult<ChatSendReply>>()
        fixture.writer.reply = { pending.await() }
        fixture.controller.edit("Текст")
        fixture.controller.send()
        runCurrent()
        fixture.controller.edit("Другой текст")
        fixture.controller.edit("Текст")
        pending.complete(AuthResult.Success(ChatSendReply.Sent(acceptedMessage)))
        runCurrent()
        assertEquals("Текст", fixture.controller.state.value.text)
        assertEquals(listOf(acceptedMessage), fixture.sent)
    }

    @Test
    fun expiredMuteRemainsBlockedUntilServerReadAndNeverReplaysDraft() = runTest {
        val fixture = composerFixture()
        val deadline = Instant.fromEpochMilliseconds(2_000)
        val mutedProfile = AuthResult.Success(fixture.auth.user.withMute(deadline))
        fixture.auth.profile = { mutedProfile }
        fixture.controller.edit("Сообщение в черновике")
        fixture.controller.syncProfile()
        runCurrent()
        assertEquals(deadline, fixture.controller.state.value.mutedUntil)

        fixture.auth.profile = { AuthResult.Failure(AuthError(AuthErrorCode.Network)) }
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(2, fixture.auth.profileReads)
        assertFalse(fixture.controller.state.value.canSend)
        fixture.controller.send()

        fixture.auth.profile = { mutedProfile }
        advanceTimeBy(29.seconds)
        runCurrent()
        assertEquals(2, fixture.auth.profileReads)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(3, fixture.auth.profileReads)
        assertTrue(fixture.controller.state.value.canSend)
        assertEquals(null, fixture.controller.state.value.mutedUntil)
        assertEquals("Сообщение в черновике", fixture.controller.state.value.text)
        assertTrue(fixture.writer.attempts.isEmpty())
    }

    @Test
    fun delayedProfileCannotOverwriteNewerMuteFromSend() = runTest {
        val fixture = composerFixture()
        val oldDeadline = Instant.fromEpochMilliseconds(3_000)
        val newerDeadline = Instant.fromEpochMilliseconds(5_000)
        val pending = CompletableDeferred<AuthResult<AuthUser>>()
        fixture.auth.profile = { pending.await() }
        fixture.controller.syncProfile()
        runCurrent()

        fixture.writer.reply = { AuthResult.Success(ChatSendReply.Muted(newerDeadline)) }
        fixture.controller.edit("Попытка отправки")
        fixture.controller.send()
        runCurrent()
        pending.complete(AuthResult.Success(fixture.auth.user.withMute(oldDeadline)))
        runCurrent()
        assertEquals(newerDeadline, fixture.controller.state.value.mutedUntil)
        assertFalse(fixture.controller.state.value.canSend)
        assertEquals(1, fixture.writer.attempts.size)
    }

    @Test
    fun profileStartedBeforeExpiryCannotLiftMuteAfterExpiry() = runTest {
        val fixture = composerFixture()
        val deadline = Instant.fromEpochMilliseconds(2_000)
        val mutedProfile = AuthResult.Success(fixture.auth.user.withMute(deadline))
        fixture.auth.profile = { mutedProfile }
        fixture.controller.edit("Черновик")
        fixture.controller.syncProfile()
        runCurrent()

        val pending = CompletableDeferred<AuthResult<AuthUser>>()
        fixture.auth.profile = { pending.await() }
        fixture.controller.syncProfile()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()
        pending.complete(mutedProfile)
        runCurrent()
        assertFalse(fixture.controller.state.value.canSend)

        fixture.auth.profile = { mutedProfile }
        fixture.controller.syncProfile()
        runCurrent()
        assertTrue(fixture.controller.state.value.canSend)
        assertTrue(fixture.writer.attempts.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.composerFixture(): ComposerFixture {
    val auth = SessionTestRepository()
    val sessions = SessionCoordinator(auth, SessionTestStore())
    sessions.restore()
    sessions.authenticate(AuthCredentials.Login("fixture", "fixture"))
    val sessionId = (sessions.state.value as SessionStatus.Active).scopeId
    val writer = ComposerTestWriter()
    val sent = mutableListOf<ChatMessage>()
    var next = 0
    val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
    }
    val controller = ChatComposerController(
        backgroundScope,
        sessions,
        writer,
        sessionId,
        "fixture-chat",
        onSent = { sent += it },
        onAccessLost = { error("Unexpected access loss: $it") },
        clock = clock,
        newKey = { "key-${++next}" },
    )
    return ComposerFixture(controller, writer, sent, auth)
}

private data class ComposerFixture(
    val controller: ChatComposerController,
    val writer: ComposerTestWriter,
    val sent: List<ChatMessage>,
    val auth: SessionTestRepository,
)

private fun AuthUser.withMute(until: Instant): AuthUser =
    AuthUser(id, displayName, avatarUrl, phone, ageYears, until, createdAt)

private class ComposerTestWriter : ChatWriteRepository {
    val attempts = mutableListOf<Pair<String, String>>()
    var reply: suspend () -> AuthResult<ChatSendReply> = { error("Unexpected send") }

    override suspend fun send(chatId: String, text: String, key: String, token: String): AuthResult<ChatSendReply> {
        attempts += text to key
        return reply()
    }

    override suspend fun report(
        chatId: String,
        targetUserId: String,
        reason: String,
        key: String,
        token: String,
    ): AuthResult<ChatReportReply> = error("Unexpected report")
}

private val acceptedMessage = ChatMessage(
    id = "fixture-message",
    chatId = "fixture-chat",
    messageSeq = 4,
    type = "text",
    author = null,
    text = "Согласовано",
    systemKind = null,
    createdAt = Instant.fromEpochMilliseconds(0),
)
