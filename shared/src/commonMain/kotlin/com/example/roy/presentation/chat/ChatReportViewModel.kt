@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.roy.presentation.chat

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.chat.ChatReportReply
import com.example.roy.domain.chat.ChatWriteRepository
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

internal data class ChatReportTarget(val userId: String, val displayName: String)

internal enum class ChatReportFeedback { Submitted, Duplicate, Forbidden, InvalidReason, Retry, Failed }

internal data class ChatReportState(
    val reason: String = "",
    val isSubmitting: Boolean = false,
    val feedback: ChatReportFeedback? = null,
    val isOnline: Boolean = true,
) {
    val isFinal: Boolean
        get() = feedback == ChatReportFeedback.Submitted || feedback == ChatReportFeedback.Duplicate ||
            feedback == ChatReportFeedback.Forbidden

    val isReasonValid: Boolean get() = isReportReasonValid(reason)
    val canSubmit: Boolean get() = isOnline && !isSubmitting && !isFinal && isReasonValid
}

internal sealed class ChatReportEvent {
    data class Edit(val reason: String) : ChatReportEvent()
    data object Submit : ChatReportEvent()
    data object Close : ChatReportEvent()
}

internal sealed class ChatReportAction {
    data object Close : ChatReportAction()
}

internal class ChatReportViewModel(
    private val target: ChatReportTarget,
    private val chatId: String,
    private val scopeId: Long,
    private val session: SessionCoordinator,
    private val writer: ChatWriteRepository,
    connectivity: Flow<Boolean> = flowOf(true),
) : BaseViewModel<ChatReportState, ChatReportEvent, ChatReportAction>(ChatReportState()) {
    private var operation: ReportOperation? = null
    private var submission: Job? = null

    init {
        viewModelScope.launch { connectivity.collect { online -> updateState { it.copy(isOnline = online) } } }
    }

    fun discard() {
        submission?.cancel()
        operation = null
        updateState { ChatReportState(isOnline = it.isOnline) }
    }

    override fun handleEvent(event: ChatReportEvent) {
        when (event) {
            is ChatReportEvent.Edit -> edit(event.reason)
            ChatReportEvent.Submit -> submit()
            ChatReportEvent.Close -> if (!viewState.value.isSubmitting) {
                discard()
                viewModelScope.launch { sendAction(ChatReportAction.Close) }
            }
        }
    }

    private fun edit(reason: String) {
        val state = viewState.value
        if (state.isSubmitting || state.isFinal || reason == state.reason) return
        if (operation?.reason != reason.trim()) operation = null
        updateState { it.copy(reason = reason, feedback = null) }
    }

    private fun submit() {
        val state = viewState.value
        if (!state.canSubmit) return
        val command = operation ?: ReportOperation(state.reason.trim(), Uuid.random().toString())
        operation = command
        updateState { it.copy(isSubmitting = true, feedback = null) }
        submission = viewModelScope.launch {
            val result = session.mutateProtected(scopeId) { token ->
                writer.report(chatId, target.userId, command.reason, command.key, token)
            }
            ensureActive()
            val feedback = when (result) {
                is AuthResult.Success -> when (result.value) {
                    ChatReportReply.Submitted -> ChatReportFeedback.Submitted
                    ChatReportReply.Duplicate -> ChatReportFeedback.Duplicate
                    ChatReportReply.Forbidden -> ChatReportFeedback.Forbidden
                    ChatReportReply.InvalidReason -> ChatReportFeedback.InvalidReason
                    ChatReportReply.KeyReused -> ChatReportFeedback.Failed
                }
                is AuthResult.Failure -> when (result.error.code) {
                    AuthErrorCode.Network, AuthErrorCode.ServiceUnavailable, AuthErrorCode.UnexpectedResponse,
                    AuthErrorCode.AuthRequired, AuthErrorCode.RateLimited -> ChatReportFeedback.Retry
                    else -> ChatReportFeedback.Failed
                }
            }
            if (feedback != ChatReportFeedback.Retry) operation = null
            updateState { it.copy(isSubmitting = false, feedback = feedback) }
        }
    }

    private data class ReportOperation(val reason: String, val key: String)
}

private fun isReportReasonValid(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || '\u0000' in trimmed || trimmed.length > 2000) return false
    var codePoints = 0
    var index = 0
    while (index < trimmed.length) {
        val char = trimmed[index++]
        if (char.isHighSurrogate() && trimmed.getOrNull(index)?.isLowSurrogate() == true) index++
        codePoints++
    }
    return codePoints <= 1000
}
