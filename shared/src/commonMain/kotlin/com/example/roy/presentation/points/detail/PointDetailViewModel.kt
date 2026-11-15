package com.example.roy.presentation.points.detail

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.PointTopic
import com.example.roy.domain.points.DetailReply
import com.example.roy.domain.points.OtherTopicKey
import com.example.roy.domain.points.PointDetailGateway
import com.example.roy.domain.points.PointTopicInput
import com.example.roy.domain.points.findTopic
import com.example.roy.domain.points.validCustomTopicLabel
import com.example.roy.presentation.core.BaseViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class PointDetailViewModel(
    private val pointId: String,
    initialTopicId: String?,
    private val scopeId: Long?,
    launchAddTopic: Boolean,
    private val operations: PointDetailGateway,
) : BaseViewModel<PointDetailState, PointDetailEvent, PointDetailAction>(
    PointDetailState(selectedTopicId = initialTopicId, isAuthorized = scopeId != null),
) {
    private var requestJob: Job? = null
    private var presetsJob: Job? = null
    private var attempt: AddAttempt? = null

    init {
        load(launchAddTopic, initialTopicId)
    }

    override fun handleEvent(event: PointDetailEvent) {
        when (event) {
            PointDetailEvent.OnBack -> onBack()
            PointDetailEvent.OnRetry -> load()
            is PointDetailEvent.OnTopicSelected -> selectTopic(event.topicId)
            PointDetailEvent.OnJoinRequested -> requestJoin()
            PointDetailEvent.OnAddRequested -> requestAdd()
            PointDetailEvent.OnPresetsRetry -> loadPresets()
            is PointDetailEvent.OnPresetSelected -> selectPreset(event.presetKey)
            is PointDetailEvent.OnCustomLabelChanged -> changeLabel(event.label)
            PointDetailEvent.OnAddConfirmed -> confirmAdd()
            PointDetailEvent.OnLeaveRequested -> requestLeave()
            PointDetailEvent.OnLeaveConfirmed -> confirmLeave()
            PointDetailEvent.OnDialogDismissed -> dismissDialog()
        }
    }

    private fun onBack() {
        if (viewState.value.operation == null) dispatch(PointDetailAction.Back)
    }

    private fun load(openAdd: Boolean = false, resumeTopicId: String? = null) {
        if (requestJob?.isActive == true || viewState.value.isGone) return
        updateState { it.copy(isLoading = true, error = null) }
        requestJob = viewModelScope.launch {
            val point = readPoint()
            updateState { it.copy(isLoading = false) }
            if (point != null && scopeId != null) {
                if (openAdd) openAddSheet()
                val topic = point.topics.firstOrNull { it.id == resumeTopicId }
                if (topic != null && viewState.value.isMembershipKnown && topic.chatId in viewState.value.memberChatIds) {
                    sendAction(chatAction(topic))
                }
            }
        }
    }

    private suspend fun readPoint(selectedTopicId: String? = null): Point? {
        val result = operations.point(pointId)
        currentCoroutineContext().ensureActive()
        return when (result) {
            is PointResult.Success -> result.value.also { point ->
                updateState { state ->
                    val presentationId = selectedTopicId ?: state.selectedTopicId
                    state.copy(
                        point = point,
                        selectedTopicId = point.topics.find { it.id == presentationId }?.id ?: point.topics.firstOrNull()?.id,
                        memberChatIds = state.memberChatIds.intersect(point.topics.map(PointTopic::chatId).toSet()),
                        isMembershipKnown = scopeId == null,
                        error = null,
                    )
                }
                readMemberships(point)
            }
            is PointResult.Failure -> {
                when (result.error) {
                    PointError.ResourceGone -> gone()
                    PointError.Network -> mutationError(PointDetailError.Network)
                    else -> mutationError(PointDetailError.Unavailable)
                }
                null
            }
        }
    }

    private suspend fun readMemberships(point: Point) {
        val scope = scopeId ?: return
        val membership = operations.memberships(pointId, scope, point.topics.map(PointTopic::chatId).toSet())
        currentCoroutineContext().ensureActive()
        when (membership) {
            is AuthResult.Success -> updateState { it.copy(memberChatIds = membership.value, isMembershipKnown = true) }
            is AuthResult.Failure -> mutationError(membership.error.code.toDetailError())
        }
    }

    private fun selectTopic(topicId: String) {
        val state = viewState.value
        if (!state.isBusy && state.point?.topics?.any { it.id == topicId } == true) {
            updateState { it.copy(selectedTopicId = topicId) }
        }
    }

    private fun requestJoin() {
        val state = viewState.value
        val topic = state.selectedTopic ?: return
        if (state.isBusy) return
        val scope = scopeId
        when {
            scope == null -> dispatch(PointDetailAction.RequireJoin(pointId, topic.id, topic.chatId))
            topic.chatId in state.memberChatIds -> dispatch(chatAction(topic))
            state.isMembershipKnown -> mutate(PointDetailOperation.Join) {
                when (val result = operations.join(topic.chatId, scope)) {
                    is DetailReply.Success -> {
                        updateState { it.copy(memberChatIds = it.memberChatIds + topic.chatId) }
                        sendAction(PointDetailAction.PointChanged(pointId, false))
                        readPoint(topic.id)
                        if (!viewState.value.isGone && topic.chatId in viewState.value.memberChatIds) {
                            sendAction(chatAction(topic))
                        }
                    }
                    DetailReply.Gone -> {
                        val current = readPoint()
                        if (current != null) {
                            sendAction(PointDetailAction.PointChanged(pointId, false))
                            mutationError(PointDetailError.Unavailable)
                        }
                    }
                    else -> handleFailure(result)
                }
            }
        }
    }

    private fun requestAdd() {
        val state = viewState.value
        if (state.isBusy || state.point == null) return
        if (scopeId == null) dispatch(PointDetailAction.RequireAdd(pointId)) else openAddSheet()
    }

    private fun openAddSheet() {
        updateState { it.copy(isAddSheetOpen = true, sheetError = null) }
        if (viewState.value.presets.isEmpty()) loadPresets()
    }

    private fun loadPresets() {
        if (!viewState.value.isAddSheetOpen || presetsJob?.isActive == true) return
        updateState { it.copy(isLoadingPresets = true, sheetError = null) }
        presetsJob = viewModelScope.launch {
            when (val result = operations.presets()) {
                is AuthResult.Success -> updateState { it.copy(presets = result.value, isLoadingPresets = false) }
                is AuthResult.Failure -> updateState {
                    it.copy(isLoadingPresets = false, sheetError = result.error.code.toDetailError())
                }
            }
        }
    }

    private fun selectPreset(key: String) {
        val state = viewState.value
        if (!state.isAddSheetOpen || state.isBusy || state.presets.none { it.key == key }) return
        updateState { it.copy(presetKey = key, sheetError = null) }
    }

    private fun changeLabel(label: String) {
        val state = viewState.value
        if (state.isAddSheetOpen && !state.isBusy) {
            updateState { it.copy(customLabel = label, sheetError = null) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun confirmAdd() {
        val state = viewState.value
        val scope = scopeId ?: return
        val key = state.presetKey ?: return
        if (!state.isAddSheetOpen || state.isBusy) return
        val customLabel = state.customLabel.trim().takeIf { key == OtherTopicKey }
        if (customLabel != null && !validCustomTopicLabel(customLabel)) {
            updateState { it.copy(sheetError = PointDetailError.InvalidTopic) }
            return
        }
        val input = PointTopicInput(key, customLabel)
        val existing = state.point?.findTopic(input)
        if (existing != null) {
            recoverExisting(existing)
            return
        }
        val current = attempt?.takeIf { it.topic == input } ?: AddAttempt(input, Uuid.random().toString())
        attempt = current
        mutate(PointDetailOperation.Add) {
            when (val result = operations.add(pointId, scope, input, current.key)) {
                is DetailReply.Success -> added(result.value)
                DetailReply.Duplicate -> recoverDuplicate(input)
                DetailReply.InvalidTopic -> {
                    attempt = null
                    updateState { it.copy(sheetError = PointDetailError.InvalidTopic) }
                }
                is DetailReply.AuthFailure -> updateState { it.copy(sheetError = result.error.code.toDetailError()) }
                DetailReply.Gone -> gone()
            }
        }
    }

    private suspend fun added(topic: PointTopic) {
        attempt = null
        closeAddSheet()
        updateState { state ->
            state.copy(
                point = state.point?.let { it.copy(topics = it.topics.filterNot { old -> old.id == topic.id } + topic) },
                selectedTopicId = topic.id,
                memberChatIds = state.memberChatIds + topic.chatId,
            )
        }
        sendAction(PointDetailAction.PointChanged(pointId, false))
        readPoint(topic.id)
        if (!viewState.value.isGone && topic.chatId in viewState.value.memberChatIds) sendAction(chatAction(topic))
    }

    private suspend fun recoverDuplicate(input: PointTopicInput) {
        attempt = null
        val refreshed = readPoint() ?: return
        val existing = refreshed.findTopic(input)
        if (existing == null) {
            updateState { it.copy(sheetError = PointDetailError.Duplicate) }
        } else {
            recoverExisting(existing)
            sendAction(PointDetailAction.PointChanged(pointId, false))
        }
    }

    private fun recoverExisting(topic: PointTopic) {
        closeAddSheet()
        updateState { it.copy(selectedTopicId = topic.id) }
    }

    private fun requestLeave() {
        val state = viewState.value
        val chatId = state.selectedTopic?.chatId ?: return
        if (!state.isBusy && chatId in state.memberChatIds) {
            updateState { it.copy(isLeaveDialogOpen = true, error = null) }
        }
    }

    private fun confirmLeave() {
        val state = viewState.value
        val scope = scopeId ?: return
        val chatId = state.selectedTopic?.chatId ?: return
        if (!state.isLeaveDialogOpen || state.isBusy || chatId !in state.memberChatIds) return
        mutate(PointDetailOperation.Leave) {
            when (val result = operations.leave(chatId, scope)) {
                is DetailReply.Success, DetailReply.Gone -> afterLeave(chatId)
                is DetailReply.AuthFailure -> {
                    updateState { it.copy(isLeaveDialogOpen = false) }
                    if (result.error.code in UncertainLeaveResults) {
                        val refreshed = readPoint()
                        val stateAfterRead = viewState.value
                        if (refreshed != null && stateAfterRead.isMembershipKnown && chatId !in stateAfterRead.memberChatIds) {
                            sendAction(PointDetailAction.PointChanged(pointId, false))
                        } else if (!stateAfterRead.isGone && stateAfterRead.error == null) {
                            mutationError(result.error.code.toDetailError())
                        }
                    } else {
                        handleFailure(result)
                    }
                }
                else -> {
                    updateState { it.copy(isLeaveDialogOpen = false) }
                    handleFailure(result)
                }
            }
        }
    }

    private suspend fun afterLeave(chatId: String) {
        updateState { it.copy(isLeaveDialogOpen = false, memberChatIds = it.memberChatIds - chatId) }
        readPoint()
        if (!viewState.value.isGone) sendAction(PointDetailAction.PointChanged(pointId, false))
    }

    private fun dismissDialog() {
        val state = viewState.value
        if (state.operation != null) return
        if (state.isAddSheetOpen) closeAddSheet()
        if (state.isLeaveDialogOpen) updateState { it.copy(isLeaveDialogOpen = false) }
    }

    private fun closeAddSheet() {
        updateState { it.copy(isAddSheetOpen = false, presetKey = null, customLabel = "", sheetError = null) }
    }

    private fun mutate(operation: PointDetailOperation, run: suspend () -> Unit) {
        updateState { it.copy(operation = operation, error = null, sheetError = null) }
        requestJob = viewModelScope.launch {
            try {
                run()
            } finally {
                updateState { it.copy(operation = null) }
            }
        }
    }

    private suspend fun handleFailure(result: DetailReply<*>) {
        when (result) {
            DetailReply.Gone -> gone()
            is DetailReply.AuthFailure -> mutationError(result.error.code.toDetailError())
            else -> mutationError(PointDetailError.Unavailable)
        }
    }

    private suspend fun gone() {
        presetsJob?.cancel()
        updateState {
            it.copy(
                point = null,
                selectedTopicId = null,
                memberChatIds = emptySet(),
                isGone = true,
                isAddSheetOpen = false,
                isLeaveDialogOpen = false,
                error = null,
            )
        }
        sendAction(PointDetailAction.PointChanged(pointId, true))
    }

    private fun mutationError(error: PointDetailError) {
        updateState { state ->
            if (state.isAddSheetOpen) state.copy(sheetError = error) else state.copy(error = error)
        }
    }

    private fun dispatch(action: PointDetailAction) {
        viewModelScope.launch { sendAction(action) }
    }

    private fun chatAction(topic: PointTopic) = PointDetailAction.OpenChat(pointId, topic.id, topic.chatId)

    private companion object {
        val UncertainLeaveResults = setOf(AuthErrorCode.Network, AuthErrorCode.ServiceUnavailable, AuthErrorCode.UnexpectedResponse)
    }

    private data class AddAttempt(val topic: PointTopicInput, val key: String)
}

private fun AuthErrorCode.toDetailError() = when (this) {
    AuthErrorCode.Network -> PointDetailError.Network
    AuthErrorCode.AuthRequired, AuthErrorCode.SessionExpired -> PointDetailError.Session
    else -> PointDetailError.Unavailable
}
