package com.example.roy.presentation.points.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.roy.domain.points.OtherTopicKey
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.components.fields.RoyTextField
import com.example.roy.presentation.theme.RoyTheme

@Composable
internal fun AddTopicSheet(
    state: PointDetailState,
    strings: PointDetailStrings,
    eventHandler: (PointDetailEvent) -> Unit,
) {
    val isSubmitting = state.operation == PointDetailOperation.Add
    val dimensions = RoyTheme.dimensions
    val typography = RoyTheme.typography
    val colors = RoyTheme.colors
    Dialog(
        onDismissRequest = { eventHandler(PointDetailEvent.OnDialogDismissed) },
        properties = DialogProperties(
            dismissOnBackPress = !isSubmitting,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .background(
                        colors.surfaceElevated,
                        RoundedCornerShape(
                            topStart = dimensions.radii.dialog,
                            topEnd = dimensions.radii.dialog,
                        ),
                    )
                    .padding(dimensions.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacing.medium),
            ) {
                BasicText(
                    strings.add,
                    modifier = Modifier.semantics { heading() },
                    style = typography.headingMedium.copy(color = colors.textPrimary),
                )
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
                ) {
                    if (state.isLoadingPresets) LoadingState(LoadingMode.Inline, strings.loading)
                    for (preset in state.presets) {
                        val isExisting = preset.key != OtherTopicKey &&
                            state.point?.topics?.any { it.presetKey == preset.key } == true
                        RoyCard(
                            modifier = Modifier.fillMaxWidth(),
                            isSelected = preset.key == state.presetKey,
                            onClick = if (isSubmitting) {
                                null
                            } else {
                                { eventHandler(PointDetailEvent.OnPresetSelected(preset.key)) }
                            },
                        ) {
                            BasicText(preset.label, style = typography.body.copy(color = colors.textPrimary))
                            if (isExisting) {
                                BasicText(strings.existing, style = typography.bodySmall.copy(color = colors.textSecondary))
                            }
                        }
                        if (preset.key == OtherTopicKey && state.presetKey == OtherTopicKey) {
                            RoyTextField(
                                value = state.customLabel,
                                label = strings.customLabel,
                                onValueChange = { eventHandler(PointDetailEvent.OnCustomLabelChanged(it)) },
                                isEnabled = !isSubmitting,
                                errorText = strings.sheetError.takeIf { state.sheetError == PointDetailError.InvalidTopic },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                onImeAction = { eventHandler(PointDetailEvent.OnAddConfirmed) },
                            )
                        }
                    }
                    strings.sheetError?.takeUnless {
                        state.sheetError == PointDetailError.InvalidTopic && state.presetKey == OtherTopicKey
                    }?.let { error ->
                        BasicText(
                            error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = typography.body.copy(color = colors.error),
                        )
                    }
                }
                if (state.presets.isEmpty() && !state.isLoadingPresets) {
                    PrimaryButton(
                        text = strings.retry,
                        onClick = { eventHandler(PointDetailEvent.OnPresetsRetry) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val isExistingPreset = state.presetKey != OtherTopicKey &&
                        state.point?.topics?.any { it.presetKey == state.presetKey } == true
                    PrimaryButton(
                        text = if (isExistingPreset) strings.selectExisting else strings.add,
                        onClick = { eventHandler(PointDetailEvent.OnAddConfirmed) },
                        modifier = Modifier.fillMaxWidth(),
                        isEnabled = state.presetKey != null,
                        isSubmitting = isSubmitting,
                    )
                }
                TertiaryButton(
                    label = strings.cancel,
                    onClick = { eventHandler(PointDetailEvent.OnDialogDismissed) },
                    modifier = Modifier.fillMaxWidth(),
                    isEnabled = !isSubmitting,
                )
            }
        }
    }
}
