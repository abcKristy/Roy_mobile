package com.example.roy.presentation.points.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.points.CreatedPointDestination
import com.example.roy.domain.points.CreatePointField
import com.example.roy.domain.points.MaxCustomTopicLength
import com.example.roy.domain.points.MaxPointAddressLength
import com.example.roy.domain.points.codePointLength
import com.example.roy.presentation.core.CollectMviActions
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.*

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun CreatePointScreen(
    viewModel: CreatePointViewModel,
    onClose: () -> Unit,
    onCreated: (CreatedPointDestination) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val eventHandler = viewModel::handleEvent
    BackHandler(enabled = state.dialog == null) {
        eventHandler(CreatePointViewEvent.OnClose)
    }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            CreatePointViewAction.Close -> onClose()
            CreatePointViewAction.SessionExpired -> onSessionExpired()
            is CreatePointViewAction.Created -> onCreated(action.destination)
        }
    }

    CreatePointView(state, resolveCreatePointText(state), eventHandler)
    when (val dialog = state.dialog) {
        is CreatePointDialog.Address -> EditCreatePointFieldDialog(
            title = stringResource(Res.string.create_point_address),
            body = if (state.suggestion == null) stringResource(Res.string.create_point_address_manual_help) else null,
            label = stringResource(Res.string.create_point_address),
            text = dialog.text,
            fieldError = if (dialog.hasError) stringResource(Res.string.create_point_address_invalid) else null,
            count = stringResource(Res.string.create_point_character_count, dialog.text.trim().codePointLength(), MaxPointAddressLength),
            confirm = stringResource(Res.string.create_point_confirm_address),
            cancel = stringResource(Res.string.create_point_cancel),
            eventHandler = eventHandler,
        )
        is CreatePointDialog.CustomTopic -> EditCreatePointFieldDialog(
            title = stringResource(Res.string.create_point_other),
            body = null,
            label = stringResource(Res.string.create_point_topic),
            text = dialog.text,
            fieldError = if (dialog.hasError) stringResource(Res.string.create_point_topic_invalid) else null,
            count = stringResource(Res.string.create_point_character_count, dialog.text.trim().codePointLength(), MaxCustomTopicLength),
            confirm = stringResource(Res.string.create_point_confirm_topic),
            cancel = stringResource(Res.string.create_point_cancel),
            eventHandler = eventHandler,
        )
        is CreatePointDialog.Nearby -> {
            val lines = mutableListOf<String>()
            for (point in dialog.points) {
                lines += stringResource(Res.string.create_point_nearby_item, point.address, point.distanceMeters.roundToInt())
            }
            ConfirmCreatePointDialog(
                title = stringResource(Res.string.create_point_nearby_title),
                body = listOf(stringResource(Res.string.create_point_nearby_body), lines.joinToString("\n"))
                    .filter(String::isNotBlank).joinToString("\n\n"),
                confirm = stringResource(Res.string.create_point_continue),
                cancel = stringResource(Res.string.create_point_cancel),
                eventHandler = eventHandler,
            )
        }
        CreatePointDialog.LastSlot -> ConfirmCreatePointDialog(
            title = stringResource(Res.string.create_point_last_slot_title),
            body = stringResource(Res.string.create_point_last_slot_body),
            confirm = stringResource(Res.string.create_point_continue),
            cancel = stringResource(Res.string.create_point_cancel),
            eventHandler = eventHandler,
        )
        is CreatePointDialog.Blocked -> ConfirmCreatePointDialog(
            title = stringResource(Res.string.create_point_blocked_title),
            body = stringResource(Res.string.create_point_blocked_body, formatCreateBlockDeadline(dialog.until)),
            confirm = stringResource(Res.string.create_point_ok),
            cancel = null,
            eventHandler = eventHandler,
        )
        null -> Unit
    }
}

@Composable
internal fun resolveCreatePointText(state: CreatePointViewState): CreatePointText {
    val issue = if (state.hasUncertainAttempt) {
        stringResource(Res.string.create_point_uncertain_attempt)
    } else state.issue?.let {
        stringResource(when (it) {
            CreatePointIssue.Connection -> Res.string.create_point_network
            CreatePointIssue.Unavailable -> Res.string.create_point_unavailable
            CreatePointIssue.InvalidInput -> Res.string.create_point_invalid
            CreatePointIssue.ReusedKey -> Res.string.create_point_key_reused
            CreatePointIssue.SessionExpired -> Res.string.create_point_session_expired
        })
    }
    val suggestion = stringResource(when (state.suggestionStatus) {
        AddressSuggestionStatus.Loading -> Res.string.create_point_address_searching
        AddressSuggestionStatus.Found -> Res.string.create_point_address_suggestion
        AddressSuggestionStatus.None -> Res.string.create_point_address_not_found
        AddressSuggestionStatus.Failed -> Res.string.create_point_address_unavailable
    })
    val quota = state.context?.quota
    return CreatePointText(
        title = stringResource(Res.string.create_point_title),
        coordinates = stringResource(Res.string.create_point_coordinates, formatCoordinate(state.draft.position.latitude), formatCoordinate(state.draft.position.longitude)),
        topicTitle = stringResource(Res.string.create_point_topic),
        presetsLoading = stringResource(Res.string.create_point_presets_loading),
        presetsFailure = stringResource(Res.string.create_point_presets_failure),
        other = stringResource(Res.string.create_point_other),
        topicError = if (state.fieldError == CreatePointField.Topic) stringResource(Res.string.create_point_topic_rejected) else null,
        addressTitle = stringResource(Res.string.create_point_address),
        addressSuggestionStatus = suggestion,
        acceptSuggestion = stringResource(Res.string.create_point_accept_suggestion),
        editAddress = stringResource(if (state.draft.address.isEmpty()) Res.string.create_point_enter_address else Res.string.create_point_edit_address),
        addressError = if (state.fieldError == CreatePointField.Address) stringResource(Res.string.create_point_address_invalid) else null,
        quotaTitle = stringResource(Res.string.create_point_quota),
        weekQuota = quota?.let { stringResource(Res.string.create_point_week_quota, it.weekUsed, it.weekLimit) },
        monthQuota = quota?.let { stringResource(Res.string.create_point_month_quota, it.monthUsed, it.monthLimit) },
        blockedUntil = state.blockedUntil?.let { stringResource(Res.string.create_point_blocked_body, formatCreateBlockDeadline(it)) },
        quotaLoading = stringResource(Res.string.create_point_quota_loading),
        quotaError = stringResource(Res.string.create_point_quota_error),
        retry = stringResource(Res.string.create_point_retry),
        publish = stringResource(if (state.hasUncertainAttempt) Res.string.create_point_retry_publish else Res.string.create_point_publish),
        close = stringResource(Res.string.create_point_back),
        issue = issue,
    )
}

internal expect fun formatCreateBlockDeadline(instant: Instant): String

private fun formatCoordinate(value: Double): String = (round(value * 100_000) / 100_000).toString()
