package com.example.roy.presentation.points.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.roy.domain.points.OtherTopicKey
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.fields.RoyTextField
import com.example.roy.presentation.theme.RoyTheme

internal data class CreatePointText(
    val title: String,
    val coordinates: String,
    val topicTitle: String,
    val presetsLoading: String,
    val presetsFailure: String,
    val other: String,
    val topicError: String?,
    val addressTitle: String,
    val addressSuggestionStatus: String,
    val acceptSuggestion: String,
    val editAddress: String,
    val addressError: String?,
    val quotaTitle: String,
    val weekQuota: String?,
    val monthQuota: String?,
    val blockedUntil: String?,
    val quotaLoading: String,
    val quotaError: String,
    val retry: String,
    val publish: String,
    val close: String,
    val issue: String?,
)

@Composable
internal fun CreatePointView(
    viewState: CreatePointViewState,
    text: CreatePointText,
    eventHandler: (CreatePointViewEvent) -> Unit,
) {
    val theme = RoyTheme.dimensions
    val canLeave = !viewState.isSubmitting && !viewState.isCreated
    val isEditable = canLeave && !viewState.hasUncertainAttempt
    Column(
        modifier = Modifier.fillMaxSize().background(RoyTheme.colors.surface).imePadding()
            .padding(horizontal = theme.spacing.medium),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(vertical = theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.large),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.extraSmall)) {
                CreatePointHeading(text.title)
                CreatePointBody(text.coordinates)
            }
            QuotaOverview(viewState, text, isEditable, eventHandler)
            TopicOptions(viewState, text, isEditable, eventHandler)
            AddressOverview(viewState, text, isEditable, eventHandler)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = theme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.extraSmall),
        ) {
            text.issue?.let { CreatePointError(it) }
            PrimaryButton(
                text = text.publish,
                onClick = { eventHandler(CreatePointViewEvent.OnPublish) },
                modifier = Modifier.fillMaxWidth(),
                isEnabled = viewState.canPublish,
                isSubmitting = viewState.isSubmitting,
            )
            TertiaryButton(
                label = text.close,
                onClick = { eventHandler(CreatePointViewEvent.OnClose) },
                modifier = Modifier.fillMaxWidth(),
                isEnabled = canLeave,
            )
        }
    }
}

@Composable
private fun QuotaOverview(
    state: CreatePointViewState,
    text: CreatePointText,
    isEditable: Boolean,
    eventHandler: (CreatePointViewEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.small)) {
        CreatePointHeading(text.quotaTitle)
        text.weekQuota?.let { CreatePointBody(it) }
        text.monthQuota?.let { CreatePointBody(it) }
        text.blockedUntil?.let { CreatePointError(it) }
        if (state.isLoadingContext) CreatePointBody(text.quotaLoading)
        if (state.hasContextError) CreatePointError(text.quotaError)
        if (!state.isLoadingContext && (state.hasContextError || state.blockedUntil != null)) {
            TertiaryButton(
                label = text.retry,
                onClick = { eventHandler(CreatePointViewEvent.OnReloadContext) },
                isEnabled = isEditable,
            )
        }
    }
}

@Composable
private fun TopicOptions(
    state: CreatePointViewState,
    text: CreatePointText,
    isEditable: Boolean,
    eventHandler: (CreatePointViewEvent) -> Unit,
) {
    val dimensions = RoyTheme.dimensions
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small)) {
        CreatePointHeading(text.topicTitle)
        when {
            state.isLoadingPresets -> CreatePointBody(text.presetsLoading)
            state.hasPresetError -> {
                CreatePointError(text.presetsFailure)
                TertiaryButton(
                    label = text.retry,
                    onClick = { eventHandler(CreatePointViewEvent.OnReloadPresets) },
                    isEnabled = isEditable,
                )
            }
            else -> Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacing.extraSmall),
            ) {
                val selected = state.draft.topic
                for (preset in state.presets) {
                    if (preset.key == OtherTopicKey) continue
                    CreateTopicOption(preset.label, selected?.presetKey == preset.key, isEditable) {
                        eventHandler(CreatePointViewEvent.OnSelectPreset(preset.key))
                    }
                }
                val isCustomTopic = selected?.presetKey == OtherTopicKey
                CreateTopicOption(
                    label = if (isCustomTopic) selected?.customLabel ?: text.other else text.other,
                    isSelected = isCustomTopic,
                    isEnabled = isEditable,
                ) { eventHandler(CreatePointViewEvent.OnSelectPreset(OtherTopicKey)) }
            }
        }
        text.topicError?.let { CreatePointError(it) }
    }
}

@Composable
private fun CreateTopicOption(
    label: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = RoyTheme.dimensions
    val colors = RoyTheme.colors
    val shape = RoundedCornerShape(theme.radii.medium)
    BasicText(
        text = label,
        modifier = Modifier.fillMaxWidth().heightIn(min = theme.minimumTouchTarget)
            .background(if (isSelected) colors.surfaceElevated else colors.surface, shape)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.accent else colors.outline, shape)
            .selectable(selected = isSelected, enabled = isEnabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = theme.spacing.medium, vertical = theme.spacing.compact),
        style = RoyTheme.typography.body.copy(color = if (isEnabled) colors.textPrimary else colors.textDisabled),
    )
}

@Composable
private fun AddressOverview(
    state: CreatePointViewState,
    text: CreatePointText,
    isEditable: Boolean,
    eventHandler: (CreatePointViewEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.small)) {
        CreatePointHeading(text.addressTitle)
        if (state.draft.address.isNotBlank()) {
            CreatePointBody(state.draft.address)
        } else {
            CreatePointBody(text.addressSuggestionStatus)
            state.suggestion?.let {
                CreatePointBody(it)
                SecondaryButton(
                    text = text.acceptSuggestion,
                    onClick = { eventHandler(CreatePointViewEvent.OnAcceptSuggestion) },
                    modifier = Modifier.fillMaxWidth(),
                    isEnabled = isEditable,
                )
            }
        }
        text.addressError?.let { CreatePointError(it) }
        TertiaryButton(
            label = text.editAddress,
            onClick = { eventHandler(CreatePointViewEvent.OnEditAddress) },
            isEnabled = isEditable,
        )
    }
}

@Composable
internal fun EditCreatePointFieldDialog(
    title: String,
    body: String?,
    label: String,
    text: String,
    fieldError: String?,
    count: String,
    confirm: String,
    cancel: String,
    eventHandler: (CreatePointViewEvent) -> Unit,
) {
    CreatePointDialogFrame(title, confirm, cancel, eventHandler) {
        if (body != null) CreatePointBody(body)
        val focus = remember { FocusRequester() }
        RoyTextField(
            value = text,
            label = label,
            onValueChange = { eventHandler(CreatePointViewEvent.OnEditDialogText(it)) },
            modifier = Modifier.focusRequester(focus),
            errorText = fieldError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onImeAction = { eventHandler(CreatePointViewEvent.OnConfirmDialog) },
        )
        CreatePointBody(count)
        LaunchedEffect(focus) { focus.requestFocus() }
    }
}

@Composable
internal fun ConfirmCreatePointDialog(
    title: String,
    body: String,
    confirm: String,
    cancel: String?,
    eventHandler: (CreatePointViewEvent) -> Unit,
) {
    CreatePointDialogFrame(title, confirm, cancel, eventHandler) { CreatePointBody(body) }
}

@Composable
private fun CreatePointDialogFrame(
    title: String,
    confirm: String,
    cancel: String?,
    eventHandler: (CreatePointViewEvent) -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = { eventHandler(CreatePointViewEvent.OnCancelDialog) },
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        val dimensions = RoyTheme.dimensions
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().imePadding()
                .background(RoyTheme.colors.surfaceElevated, RoundedCornerShape(dimensions.radii.dialog))
                .verticalScroll(rememberScrollState()).padding(dimensions.spacing.large),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacing.compact),
        ) {
            CreatePointHeading(title)
            content()
            PrimaryButton(confirm, { eventHandler(CreatePointViewEvent.OnConfirmDialog) }, Modifier.fillMaxWidth())
            if (cancel != null) {
                SecondaryButton(cancel, { eventHandler(CreatePointViewEvent.OnCancelDialog) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CreatePointHeading(text: String) {
    BasicText(
        text,
        modifier = Modifier.semantics { heading() },
        style = RoyTheme.typography.headingSmall.copy(color = RoyTheme.colors.textPrimary),
    )
}

@Composable
private fun CreatePointBody(text: String) {
    BasicText(text, style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textSecondary))
}

@Composable
private fun CreatePointError(text: String) {
    BasicText(
        text,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.error),
    )
}
