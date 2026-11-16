package com.example.roy.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.fields.RoyTextField
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.chat_report_cancel
import roy.shared.generated.resources.chat_report_close
import roy.shared.generated.resources.chat_report_duplicate
import roy.shared.generated.resources.chat_report_failed
import roy.shared.generated.resources.chat_report_forbidden
import roy.shared.generated.resources.chat_report_invalid
import roy.shared.generated.resources.chat_report_reason
import roy.shared.generated.resources.chat_report_retry
import roy.shared.generated.resources.chat_report_submit
import roy.shared.generated.resources.chat_report_submitted
import roy.shared.generated.resources.chat_report_title
import roy.shared.generated.resources.auth_network

@Composable
internal fun ChatReportDialog(target: ChatReportTarget, model: ChatReportViewModel, onDismiss: () -> Unit) {
    val state by model.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(model) { model::handleEvent }
    DisposableEffect(model) { onDispose { model.discard() } }
    CollectMviActions(model.viewAction) { onDismiss() }
    ChatReportView(target, state, eventHandler)
}

@Composable
internal fun ChatReportView(
    target: ChatReportTarget,
    viewState: ChatReportState,
    eventHandler: (ChatReportEvent) -> Unit,
) {
    val colors = RoyTheme.colors
    val type = RoyTheme.typography
    val dimensions = RoyTheme.dimensions
    Dialog(
        onDismissRequest = { eventHandler(ChatReportEvent.Close) },
        properties = DialogProperties(dismissOnBackPress = !viewState.isSubmitting, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().imePadding()
                .background(colors.surfaceElevated, RoundedCornerShape(dimensions.radii.dialog))
                .verticalScroll(rememberScrollState()).padding(dimensions.spacing.large),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacing.compact),
        ) {
            BasicText(
                stringResource(Res.string.chat_report_title, target.displayName),
                Modifier.semantics { heading() },
                style = type.headingSmall.copy(color = colors.textPrimary),
            )
            if (!viewState.isFinal) {
                RoyTextField(
                    value = viewState.reason,
                    label = stringResource(Res.string.chat_report_reason),
                    onValueChange = { eventHandler(ChatReportEvent.Edit(it)) },
                    isEnabled = !viewState.isSubmitting,
                    errorText = if (viewState.reason.isNotBlank() && !viewState.isSubmitting && !viewState.isReasonValid)
                        stringResource(Res.string.chat_report_invalid) else null,
                )
            }
            viewState.feedback?.let { feedback ->
                val message = when (feedback) {
                    ChatReportFeedback.Submitted -> Res.string.chat_report_submitted
                    ChatReportFeedback.Duplicate -> Res.string.chat_report_duplicate
                    ChatReportFeedback.Forbidden -> Res.string.chat_report_forbidden
                    ChatReportFeedback.InvalidReason -> Res.string.chat_report_invalid
                    ChatReportFeedback.Retry -> Res.string.chat_report_retry
                    ChatReportFeedback.Failed -> Res.string.chat_report_failed
                }
                BasicText(
                    stringResource(message),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = type.body.copy(color = colors.textPrimary),
                )
            }
            if (!viewState.isFinal) {
                if (!viewState.isOnline) {
                    BasicText(stringResource(Res.string.auth_network), style = type.body.copy(color = colors.textPrimary))
                }
                PrimaryButton(
                    text = stringResource(Res.string.chat_report_submit),
                    onClick = { eventHandler(ChatReportEvent.Submit) },
                    modifier = Modifier.fillMaxWidth(),
                    isEnabled = viewState.canSubmit,
                    isSubmitting = viewState.isSubmitting,
                )
            }
            SecondaryButton(
                text = stringResource(if (viewState.isFinal) Res.string.chat_report_close else Res.string.chat_report_cancel),
                onClick = { eventHandler(ChatReportEvent.Close) },
                modifier = Modifier.fillMaxWidth(),
                isEnabled = !viewState.isSubmitting,
            )
        }
    }
}
