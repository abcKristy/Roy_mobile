@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.roy.presentation.chat

import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.domain.chat.ChatSendReply
import com.example.roy.domain.chat.ChatWriteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal enum class ChatComposerIssue {
    Uncertain,
    RejectedPhone,
    RejectedUrl,
    RejectedProfanity,
    RejectedPattern,
    InvalidText,
    SendFailed,
}

internal data class ChatComposerState(
    val text: String = "",
    val isSending: Boolean = false,
    val mutedUntil: Instant? = null,
    val retryAfterSeconds: Int = 0,
    val issue: ChatComposerIssue? = null,
) {
    val canSend: Boolean
        get() = text.isNotBlank() && !isSending && mutedUntil == null && retryAfterSeconds == 0
}

/** Owned by the active chat ViewModel. Calls and state updates run in its UI scope. */
internal class ChatComposerController(
    private val scope: CoroutineScope,
    private val sessions: SessionCoordinator,
    private val repository: ChatWriteRepository,
    private val scopeId: Long,
    private val chatId: String,
    private val onSent: (ChatMessage) -> Unit,
    private val onAccessLost: (ChatSendReply) -> Unit,
    private val clock: Clock = Clock.System,
    private val newKey: () -> String = { Uuid.random().toString() },
) {
    private val mutableState = MutableStateFlow(ChatComposerState())
    val state = mutableState.asStateFlow()
    private var retryable: SendAttempt? = null
    private var floodUntil: Instant? = null
    private var mutedUntil: Instant? = null
    private var countdown: Job? = null
    private var profile: Job? = null
    private var sending: Job? = null
    private var revoked = false
    private var draftRevision = 0L

    init {
        scope.launch {
            sessions.state.first { it != SessionStatus.Active(scopeId) }
            revoke()
        }
    }

    fun edit(text: String) {
        if (!active || text == state.value.text) return
        retryable = null
        draftRevision++
        mutableState.value = state.value.copy(text = text, issue = null)
    }

    fun send() {
        if (!active) return
        refreshClock()
        val current = state.value
        if (!current.canSend) return
        val attempt = retryable ?: SendAttempt(current.text, newKey(), draftRevision)
        mutableState.value = current.copy(isSending = true, issue = null)
        sending = scope.launch {
            val reply = sessions.mutateProtected(scopeId) { token ->
                repository.send(chatId, attempt.text, attempt.key, token)
            }
            if (!active) return@launch
            val draftMatches = draftRevision == attempt.revision
            mutableState.value = state.value.copy(isSending = false)
            retryable = null
            when (reply) {
                is AuthResult.Success -> applyReply(reply.value, draftMatches)
                is AuthResult.Failure -> {
                    if (draftMatches) {
                        val uncertain = reply.error.code in setOf(
                            AuthErrorCode.Network,
                            AuthErrorCode.ServiceUnavailable,
                            AuthErrorCode.UnexpectedResponse,
                        )
                        if (uncertain) retryable = attempt
                        mutableState.value = state.value.copy(
                            issue = if (uncertain) ChatComposerIssue.Uncertain else ChatComposerIssue.SendFailed,
                        )
                    }
                }
            }
        }
    }

    fun syncProfile() {
        if (!active) return
        refreshClock()
        if (profile?.isActive == true) return
        profile = scope.launch {
            val result = sessions.currentUser(scopeId)
            if (active && result is AuthResult.Success) {
                result.value.messageMutedUntil?.let(::applyMute)
            }
        }
    }

    fun revoke() {
        revoked = true
        sending?.cancel()
        profile?.cancel()
        countdown?.cancel()
        retryable = null
        mutableState.value = ChatComposerState()
    }

    private val active: Boolean
        get() = !revoked && sessions.state.value == SessionStatus.Active(scopeId)

    private fun applyReply(reply: ChatSendReply, draftMatches: Boolean) {
        val issue = when (reply) {
            is ChatSendReply.Sent -> {
                if (draftMatches) mutableState.value = state.value.copy(text = "")
                onSent(reply.message)
                null
            }
            is ChatSendReply.Muted -> {
                applyMute(reply.until)
                null
            }
            is ChatSendReply.Flood -> {
                floodUntil = clock.now() + reply.retryAfterSeconds.seconds
                startCountdown()
                null
            }
            ChatSendReply.Gone, ChatSendReply.NotMember -> {
                revoke()
                onAccessLost(reply)
                null
            }
            is ChatSendReply.Rejected -> when (reply.category) {
                "phone" -> ChatComposerIssue.RejectedPhone
                "url" -> ChatComposerIssue.RejectedUrl
                "profanity" -> ChatComposerIssue.RejectedProfanity
                else -> ChatComposerIssue.RejectedPattern
            }
            ChatSendReply.InvalidText -> ChatComposerIssue.InvalidText
            ChatSendReply.KeyReused -> ChatComposerIssue.SendFailed
        }
        if (issue != null && draftMatches) mutableState.value = state.value.copy(issue = issue)
    }

    private fun applyMute(until: Instant) {
        mutedUntil = maxOf(mutedUntil ?: until, until)
        startCountdown()
    }

    private fun startCountdown() {
        refreshClock()
        if (countdown?.isActive == true) return
        countdown = scope.launch {
            while (active && (state.value.mutedUntil != null || state.value.retryAfterSeconds > 0)) {
                val wait = if (state.value.retryAfterSeconds > 0) 1.seconds
                else (requireNotNull(state.value.mutedUntil) - clock.now()).coerceAtLeast(1.seconds)
                delay(wait)
                refreshClock()
            }
        }
    }

    private fun refreshClock() {
        val now = clock.now()
        val remaining = floodUntil?.let { it - now }
        val seconds = if (remaining != null && remaining.isPositive()) {
            val whole = remaining.inWholeSeconds
            (whole + if (remaining > whole.seconds) 1 else 0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else 0
        mutableState.value = state.value.copy(
            mutedUntil = mutedUntil?.takeIf { it > now },
            retryAfterSeconds = seconds,
        )
    }

    private data class SendAttempt(val text: String, val key: String, val revision: Long)
}
