package com.example.roy.presentation.points.create

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.map.Position
import com.example.roy.domain.points.CreatePointAttempt
import com.example.roy.domain.points.CreatePointDraft
import com.example.roy.domain.points.CreatePointField
import com.example.roy.domain.points.CreatePointOutcome
import com.example.roy.domain.points.CreatePointUseCase
import com.example.roy.domain.points.CreationContext
import com.example.roy.domain.points.NearbyPoint
import com.example.roy.domain.points.OTHER_TOPIC_KEY
import com.example.roy.domain.points.PointAddressSource
import com.example.roy.domain.points.PointTopicInput
import com.example.roy.domain.points.validCustomTopicLabel
import com.example.roy.domain.points.validPointAddress
import com.example.roy.presentation.core.BaseViewModel
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

internal enum class CreatePointIssue {
    Connection,
    Unavailable,
    InvalidInput,
    ReusedKey,
    SessionExpired,
}

internal enum class AddressSuggestionStatus {
    Loading,
    Found,
    None,
    Failed,
}

internal sealed class CreatePointDialog {
    data class Address(val text: String, val hasError: Boolean = false) : CreatePointDialog()
    data class CustomTopic(val text: String, val hasError: Boolean = false) : CreatePointDialog()
    data class Nearby(val points: List<NearbyPoint>) : CreatePointDialog()
    data object LastSlot : CreatePointDialog()
    data class Blocked(val until: Instant) : CreatePointDialog()
}

internal class CreatePointViewModel(
    position: Position,
    private val useCase: CreatePointUseCase,
) : BaseViewModel<CreatePointViewState, CreatePointViewEvent, CreatePointViewAction>(
    CreatePointViewState(CreatePointDraft(position)),
) {
    private var attempt: CreatePointAttempt? = null
    private var hasAcceptedLastSlot = false
    private var hasReportedSessionExpiry = false

    init {
        loadPresets()
        loadContext()
        loadSuggestion(position)
    }

    override fun handleEvent(event: CreatePointViewEvent) {
        val state = viewState.value
        if (state.isSubmitting || state.isCreated) return
        if (state.hasUncertainAttempt && event !in uncertainAttemptEvents) return
        if (state.dialog != null && event !in dialogEvents && event !is CreatePointViewEvent.OnEditDialogText) return
        when (event) {
            CreatePointViewEvent.OnClose -> onClose()
            CreatePointViewEvent.OnReloadPresets -> onReloadPresets()
            CreatePointViewEvent.OnReloadContext -> onReloadContext()
            is CreatePointViewEvent.OnSelectPreset -> onSelectPreset(event.key)
            CreatePointViewEvent.OnAcceptSuggestion -> onAcceptSuggestion()
            CreatePointViewEvent.OnEditAddress -> onEditAddress()
            is CreatePointViewEvent.OnEditDialogText -> onEditDialogText(event.text)
            CreatePointViewEvent.OnConfirmDialog -> onConfirmDialog()
            CreatePointViewEvent.OnCancelDialog -> onCancelDialog()
            CreatePointViewEvent.OnPublish -> onPublish()
        }
    }

    private fun onReloadPresets() {
        val state = viewState.value
        if (!state.isLoadingPresets) loadPresets()
    }

    private fun onReloadContext() {
        val state = viewState.value
        if (!state.isLoadingContext) loadContext()
    }

    private fun onEditAddress() {
        openAddressEditor()
    }

    private fun onClose() {
        viewModelScope.launch { sendAction(CreatePointViewAction.Close) }
    }

    private fun loadPresets() {
        updateForm { it.copy(isLoadingPresets = true, hasPresetError = false) }
        viewModelScope.launch {
            when (val result = useCase.getPresets()) {
                is AuthResult.Success -> updateForm { it.copy(presets = result.value, isLoadingPresets = false) }
                is AuthResult.Failure -> updateForm {
                    it.copy(isLoadingPresets = false, hasPresetError = true)
                }
            }
        }
    }

    private fun loadContext() {
        updateForm { it.copy(isLoadingContext = true, hasContextError = false) }
        viewModelScope.launch {
            when (val result = useCase.getContext(viewState.value.draft.position)) {
                is AuthResult.Success -> showContext(result.value)
                is AuthResult.Failure -> {
                    updateForm { it.copy(isLoadingContext = false, hasContextError = true) }
                    handleSessionError(result.error.code)
                }
            }
        }
    }

    private fun showContext(context: CreationContext) {
        val deadline = context.quota.blockedUntil
        updateForm {
            it.copy(
                context = context,
                isLoadingContext = false,
                blockedUntil = deadline,
                dialog = if (deadline != null && it.dialog == null) CreatePointDialog.Blocked(deadline) else it.dialog,
            )
        }
    }

    private fun loadSuggestion(position: Position) {
        viewModelScope.launch {
            when (val result = useCase.getAddress(position)) {
                is AuthResult.Success -> updateForm {
                    it.copy(
                        suggestion = result.value,
                        suggestionStatus = if (result.value == null) AddressSuggestionStatus.None
                            else AddressSuggestionStatus.Found,
                    )
                }
                is AuthResult.Failure -> {
                    updateForm { it.copy(suggestionStatus = AddressSuggestionStatus.Failed) }
                    handleSessionError(result.error.code)
                }
            }
        }
    }

    private fun onSelectPreset(key: String) {
        val state = viewState.value
        if (key == OTHER_TOPIC_KEY) {
            updateForm {
                it.copy(dialog = CreatePointDialog.CustomTopic(state.draft.topic?.customLabel.orEmpty()))
            }
        } else if (state.presets.any { it.key == key }) {
            editDraft(CreatePointField.Topic) { copy(topic = PointTopicInput(key)) }
        }
    }

    private fun onAcceptSuggestion() {
        val suggestion = viewState.value.suggestion ?: return
        editDraft(CreatePointField.Address) { copy(address = suggestion, addressSource = PointAddressSource.Provider) }
    }

    private fun openAddressEditor() {
        val state = viewState.value
        val initial = state.draft.address.ifBlank { state.suggestion.orEmpty() }
        updateForm { it.copy(dialog = CreatePointDialog.Address(initial)) }
    }

    private fun onEditDialogText(value: String) = updateForm {
        val dialog = when (val current = it.dialog) {
            is CreatePointDialog.Address -> current.copy(text = value, hasError = false)
            is CreatePointDialog.CustomTopic -> current.copy(text = value, hasError = false)
            else -> current
        }
        it.copy(dialog = dialog)
    }

    private fun onConfirmDialog() {
        when (val dialog = viewState.value.dialog) {
            is CreatePointDialog.Address -> confirmAddress(dialog)
            is CreatePointDialog.CustomTopic -> confirmCustomTopic(dialog)
            is CreatePointDialog.Nearby -> confirmNearby()
            CreatePointDialog.LastSlot -> confirmLastSlot()
            is CreatePointDialog.Blocked -> onCancelDialog()
            null -> Unit
        }
    }

    private fun confirmAddress(dialog: CreatePointDialog.Address) {
        if (!validPointAddress(dialog.text)) {
            updateForm { it.copy(dialog = dialog.copy(hasError = true)) }
            return
        }
        editDraft(CreatePointField.Address) {
            copy(address = dialog.text.trim(), addressSource = PointAddressSource.Manual)
        }
    }

    private fun confirmCustomTopic(dialog: CreatePointDialog.CustomTopic) {
        if (!validCustomTopicLabel(dialog.text)) {
            updateForm { it.copy(dialog = dialog.copy(hasError = true)) }
            return
        }
        editDraft(CreatePointField.Topic) {
            copy(topic = PointTopicInput(OTHER_TOPIC_KEY, dialog.text.trim()))
        }
    }

    private fun editDraft(field: CreatePointField, edit: CreatePointDraft.() -> CreatePointDraft) {
        val state = viewState.value
        val updated = state.draft.edit()
        val payloadChanged = updated != state.draft
        if (payloadChanged) {
            attempt = null
            hasAcceptedLastSlot = false
        }
        updateForm {
            it.copy(
                draft = updated,
                dialog = null,
                fieldError = it.fieldError?.takeUnless { previous -> previous == field },
                issue = it.issue.takeUnless { payloadChanged },
                hasUncertainAttempt = it.hasUncertainAttempt && !payloadChanged,
            )
        }
    }

    private fun onCancelDialog() {
        when (viewState.value.dialog) {
            is CreatePointDialog.Nearby, CreatePointDialog.LastSlot -> {
                attempt = null
                hasAcceptedLastSlot = false
            }
            else -> Unit
        }
        updateForm { it.copy(dialog = null) }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun onPublish() {
        val state = viewState.value
        if (!state.canPublish) return
        val payload = useCase.validatedPayload(state.draft, state.presets) ?: return
        val previous = attempt
        if (state.hasUncertainAttempt && previous?.payload == payload) {
            submit(previous)
            return
        }
        val current = CreatePointAttempt(payload, Uuid.random().toString())
        attempt = current
        hasAcceptedLastSlot = false
        updateForm { it.copy(isSubmitting = true, issue = null, fieldError = null) }
        viewModelScope.launch {
            when (val result = useCase.getContext(payload.position)) {
                is AuthResult.Success -> {
                    showContext(result.value)
                    if (result.value.quota.blockedUntil != null) {
                        attempt = null
                        updateForm { it.copy(isSubmitting = false) }
                    } else if (result.value.nearby.isNotEmpty()) {
                        updateForm { it.copy(isSubmitting = false, dialog = CreatePointDialog.Nearby(result.value.nearby)) }
                    } else {
                        warnLastSlotOrSubmit(current)
                    }
                }
                is AuthResult.Failure -> {
                    attempt = null
                    updateForm { it.copy(isSubmitting = false, issue = result.error.code.toIssue()) }
                    handleSessionError(result.error.code)
                }
            }
        }
    }

    private fun confirmNearby() {
        val confirmed = attempt?.copy(confirmNearby = true) ?: return onCancelDialog()
        attempt = confirmed
        warnLastSlotOrSubmit(confirmed)
    }

    private fun warnLastSlotOrSubmit(current: CreatePointAttempt) {
        if (viewState.value.context?.quota?.lastAvailableWarning == true && !hasAcceptedLastSlot) {
            updateForm { it.copy(isSubmitting = false, dialog = CreatePointDialog.LastSlot) }
        } else {
            submit(current)
        }
    }

    private fun confirmLastSlot() {
        val current = attempt ?: return onCancelDialog()
        hasAcceptedLastSlot = true
        submit(current)
    }

    private fun submit(current: CreatePointAttempt) {
        updateForm {
            it.copy(isSubmitting = true, hasUncertainAttempt = false, dialog = null, issue = null, fieldError = null)
        }
        viewModelScope.launch {
            when (val result = useCase.submit(current)) {
                is AuthResult.Success -> handleOutcome(result.value)
                is AuthResult.Failure -> {
                    updateForm {
                        it.copy(isSubmitting = false, hasUncertainAttempt = true, issue = result.error.code.toIssue())
                    }
                    handleSessionError(result.error.code)
                }
            }
        }
    }

    private suspend fun handleOutcome(outcome: CreatePointOutcome) {
        when (outcome) {
            is CreatePointOutcome.Created -> {
                attempt = null
                updateForm { it.copy(isSubmitting = false, isCreated = true) }
                sendAction(CreatePointViewAction.Created(outcome.destination))
            }
            is CreatePointOutcome.ConfirmNearby -> {
                updateForm { it.copy(isSubmitting = false, dialog = CreatePointDialog.Nearby(outcome.points)) }
            }
            is CreatePointOutcome.Blocked -> {
                attempt = null
                updateForm {
                    it.copy(isSubmitting = false, blockedUntil = outcome.until, dialog = CreatePointDialog.Blocked(outcome.until))
                }
            }
            is CreatePointOutcome.Invalid -> {
                attempt = null
                updateForm {
                    it.copy(
                        isSubmitting = false,
                        fieldError = outcome.field,
                        issue = CreatePointIssue.InvalidInput.takeIf {
                            outcome.field == null || outcome.field == CreatePointField.Position
                        },
                    )
                }
            }
            CreatePointOutcome.KeyReused -> {
                attempt = null
                updateForm { it.copy(isSubmitting = false, issue = CreatePointIssue.ReusedKey) }
            }
        }
    }

    private fun handleSessionError(code: AuthErrorCode) {
        if (hasReportedSessionExpiry ||
            (code != AuthErrorCode.SessionExpired && code != AuthErrorCode.AuthRequired)
        ) return
        hasReportedSessionExpiry = true
        viewModelScope.launch { sendAction(CreatePointViewAction.SessionExpired) }
    }

    private fun updateForm(transform: (CreatePointViewState) -> CreatePointViewState) = updateState {
        val updated = transform(it)
        updated.copy(
            canPublish = !updated.isLoadingContext && !updated.isLoadingPresets && !updated.hasPresetError &&
                !updated.isSubmitting && !updated.isCreated && updated.dialog == null &&
                (updated.blockedUntil == null || updated.hasUncertainAttempt) &&
                updated.fieldError == null && updated.issue != CreatePointIssue.ReusedKey &&
                useCase.validatedPayload(updated.draft, updated.presets) != null,
        )
    }
}

private val uncertainAttemptEvents = setOf(CreatePointViewEvent.OnClose, CreatePointViewEvent.OnPublish)
private val dialogEvents = setOf(CreatePointViewEvent.OnConfirmDialog, CreatePointViewEvent.OnCancelDialog)

private fun AuthErrorCode.toIssue() = when (this) {
    AuthErrorCode.Network -> CreatePointIssue.Connection
    AuthErrorCode.AuthRequired, AuthErrorCode.SessionExpired -> CreatePointIssue.SessionExpired
    else -> CreatePointIssue.Unavailable
}
