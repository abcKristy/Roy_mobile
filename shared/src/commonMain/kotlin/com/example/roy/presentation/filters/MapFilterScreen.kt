package com.example.roy.presentation.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.points.OtherTopicKey
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.components.fields.RoyTextField
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.*

@Composable
internal fun MapFilterScreen(
    viewModel: MapFilterViewModel,
    onBack: () -> Unit,
    onApplied: (PointFilter) -> Unit,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(viewModel) { viewModel::handleEvent }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            is MapFilterAction.Applied -> onApplied(action.filter)
            MapFilterAction.Back -> onBack()
        }
    }
    MapFilterView(state, eventHandler)
}

@Composable
internal fun MapFilterView(
    state: MapFilterViewState,
    eventHandler: (MapFilterEvent) -> Unit,
) {
    val spacing = RoyTheme.dimensions.spacing
    Column(
        Modifier.fillMaxSize().background(RoyTheme.colors.background)
            .imePadding().verticalScroll(rememberScrollState()).padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        TertiaryButton(stringResource(Res.string.protected_back), { eventHandler(MapFilterEvent.Back) })
        BasicText(
            stringResource(Res.string.map_filter_title),
            modifier = Modifier.semantics { heading() },
            style = RoyTheme.typography.headingMedium.copy(color = RoyTheme.colors.textPrimary),
        )
        BasicText(
            stringResource(Res.string.map_filter_people),
            style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textPrimary),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            val keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            RoyTextField(
                state.peopleMin, stringResource(Res.string.map_filter_min),
                { eventHandler(MapFilterEvent.PeopleMin(it)) },
                modifier = Modifier.weight(1f), keyboardOptions = keyboardOptions,
            )
            RoyTextField(
                state.peopleMax, stringResource(Res.string.map_filter_max),
                { eventHandler(MapFilterEvent.PeopleMax(it)) },
                modifier = Modifier.weight(1f), keyboardOptions = keyboardOptions,
            )
        }
        if (!state.rangeValid) {
            BasicText(
                stringResource(Res.string.map_filter_range_error),
                style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.error),
            )
        }
        BasicText(
            stringResource(Res.string.map_filter_topic),
            style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textPrimary),
        )
        Column(Modifier.selectableGroup()) {
            FilterTopicChoice(
                stringResource(Res.string.map_filter_any_topic), state.topicKey == null,
            ) { eventHandler(MapFilterEvent.Topic(null)) }
            state.presets.forEach { preset ->
                FilterTopicChoice(preset.label, state.topicKey == preset.key) {
                    eventHandler(MapFilterEvent.Topic(preset.key))
                }
            }
        }
        if (state.loadingPresets) {
            LoadingState(LoadingMode.Inline, stringResource(Res.string.map_filter_loading))
        }
        if (state.presetError) {
            ErrorState(
                safeMessage = stringResource(Res.string.map_filter_presets_failed),
                isRetryEnabled = true,
                onRetry = { eventHandler(MapFilterEvent.RetryPresets) },
            )
        }
        if (state.topicKey == OtherTopicKey) {
            RoyTextField(
                state.customLabel, stringResource(Res.string.map_filter_custom),
                { eventHandler(MapFilterEvent.CustomLabel(it)) },
                errorText = if (state.labelValid) null else stringResource(Res.string.map_filter_label_error),
            )
        }
        PrimaryButton(
            stringResource(Res.string.map_filter_apply),
            { eventHandler(MapFilterEvent.Apply) },
            modifier = Modifier.fillMaxWidth(),
            isEnabled = state.rangeValid && state.labelValid && !state.submitting,
        )
        TertiaryButton(
            stringResource(Res.string.map_reset_filter),
            { eventHandler(MapFilterEvent.Reset) },
            isEnabled = !state.submitting,
        )
    }
}

@Composable
private fun FilterTopicChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = RoyTheme.colors
    val spacing = RoyTheme.dimensions.spacing
    Row(
        Modifier.fillMaxWidth().heightIn(min = RoyTheme.dimensions.minimumTouchTarget)
            .background(if (selected) theme.secondary else theme.background)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = RoyTheme.typography.body.copy(color = theme.textPrimary))
    }
}
