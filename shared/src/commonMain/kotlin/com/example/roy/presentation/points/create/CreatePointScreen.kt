package com.example.roy.presentation.points.create

import androidx.compose.runtime.Composable
import com.example.roy.presentation.core.formatLocalDateTime
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.points.CreatedPointDestination
import com.example.roy.domain.points.CreatePointField
import com.example.roy.domain.points.MAX_CUSTOM_TOPIC_LENGTH
import com.example.roy.domain.points.MAX_POINT_ADDRESS_LENGTH
import com.example.roy.domain.points.codePointLength
import com.example.roy.presentation.core.CollectMviActions
import kotlin.math.round
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.create_point_accept_suggestion
import roy.shared.generated.resources.create_point_address
import roy.shared.generated.resources.create_point_address_invalid
import roy.shared.generated.resources.create_point_address_manual_help
import roy.shared.generated.resources.create_point_address_not_found
import roy.shared.generated.resources.create_point_address_searching
import roy.shared.generated.resources.create_point_address_suggestion
import roy.shared.generated.resources.create_point_address_unavailable
import roy.shared.generated.resources.create_point_back
import roy.shared.generated.resources.create_point_blocked_body
import roy.shared.generated.resources.create_point_blocked_title
import roy.shared.generated.resources.create_point_cancel
import roy.shared.generated.resources.create_point_character_count
import roy.shared.generated.resources.create_point_confirm_address
import roy.shared.generated.resources.create_point_confirm_topic
import roy.shared.generated.resources.create_point_continue
import roy.shared.generated.resources.create_point_coordinates
import roy.shared.generated.resources.create_point_edit_address
import roy.shared.generated.resources.create_point_enter_address
import roy.shared.generated.resources.create_point_invalid
import roy.shared.generated.resources.create_point_key_reused
import roy.shared.generated.resources.create_point_last_slot_body
import roy.shared.generated.resources.create_point_last_slot_title
import roy.shared.generated.resources.create_point_month_quota
import roy.shared.generated.resources.create_point_nearby_body
import roy.shared.generated.resources.create_point_nearby_item
import roy.shared.generated.resources.create_point_nearby_title
import roy.shared.generated.resources.create_point_network
import roy.shared.generated.resources.create_point_ok
import roy.shared.generated.resources.create_point_other
import roy.shared.generated.resources.create_point_presets_failure
import roy.shared.generated.resources.create_point_presets_loading
import roy.shared.generated.resources.create_point_publish
import roy.shared.generated.resources.create_point_quota
import roy.shared.generated.resources.create_point_quota_error
import roy.shared.generated.resources.create_point_quota_loading
import roy.shared.generated.resources.create_point_retry
import roy.shared.generated.resources.create_point_retry_publish
import roy.shared.generated.resources.create_point_session_expired
import roy.shared.generated.resources.create_point_title
import roy.shared.generated.resources.create_point_topic
import roy.shared.generated.resources.create_point_topic_invalid
import roy.shared.generated.resources.create_point_topic_rejected
import roy.shared.generated.resources.create_point_unavailable
import roy.shared.generated.resources.create_point_uncertain_attempt
import roy.shared.generated.resources.create_point_week_quota

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun CreatePointScreen(
    viewModel: CreatePointViewModel,
    onClose: () -> Unit,
    onCreated: (CreatedPointDestination) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val eventHandler = viewModel::handleEvent
    BackHandler(enabled = viewState.dialog == null) {
        eventHandler(CreatePointViewEvent.OnClose)
    }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            CreatePointViewAction.Close -> onClose()
            CreatePointViewAction.SessionExpired -> onSessionExpired()
            is CreatePointViewAction.Created -> onCreated(action.destination)
        }
    }

    CreatePointView(viewState, resolveCreatePointText(viewState), eventHandler)
    when (val dialog = viewState.dialog) {
        is CreatePointDialog.Address -> EditCreatePointFieldDialog(
            title = stringResource(Res.string.create_point_address),
            body = if (viewState.suggestion == null) stringResource(Res.string.create_point_address_manual_help) else null,
            label = stringResource(Res.string.create_point_address),
            text = dialog.text,
            fieldError = if (dialog.hasError) stringResource(Res.string.create_point_address_invalid) else null,
            count = stringResource(Res.string.create_point_character_count, dialog.text.trim().codePointLength(), MAX_POINT_ADDRESS_LENGTH),
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
            count = stringResource(Res.string.create_point_character_count, dialog.text.trim().codePointLength(), MAX_CUSTOM_TOPIC_LENGTH),
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
            body = stringResource(Res.string.create_point_blocked_body, formatLocalDateTime(dialog.until)),
            confirm = stringResource(Res.string.create_point_ok),
            cancel = null,
            eventHandler = eventHandler,
        )
        null -> Unit
    }
}

@Composable
internal fun resolveCreatePointText(viewState: CreatePointViewState): CreatePointText {
    val issue = if (viewState.hasUncertainAttempt) {
        stringResource(Res.string.create_point_uncertain_attempt)
    } else viewState.issue?.let {
        stringResource(when (it) {
            CreatePointIssue.Connection -> Res.string.create_point_network
            CreatePointIssue.Unavailable -> Res.string.create_point_unavailable
            CreatePointIssue.InvalidInput -> Res.string.create_point_invalid
            CreatePointIssue.ReusedKey -> Res.string.create_point_key_reused
            CreatePointIssue.SessionExpired -> Res.string.create_point_session_expired
        })
    }
    val suggestion = stringResource(when (viewState.suggestionStatus) {
        AddressSuggestionStatus.Loading -> Res.string.create_point_address_searching
        AddressSuggestionStatus.Found -> Res.string.create_point_address_suggestion
        AddressSuggestionStatus.None -> Res.string.create_point_address_not_found
        AddressSuggestionStatus.Failed -> Res.string.create_point_address_unavailable
    })
    val quota = viewState.context?.quota
    return CreatePointText(
        title = stringResource(Res.string.create_point_title),
        coordinates = stringResource(Res.string.create_point_coordinates, formatCoordinate(viewState.draft.position.latitude), formatCoordinate(viewState.draft.position.longitude)),
        topicTitle = stringResource(Res.string.create_point_topic),
        presetsLoading = stringResource(Res.string.create_point_presets_loading),
        presetsFailure = stringResource(Res.string.create_point_presets_failure),
        other = stringResource(Res.string.create_point_other),
        topicError = if (viewState.fieldError == CreatePointField.Topic) stringResource(Res.string.create_point_topic_rejected) else null,
        addressTitle = stringResource(Res.string.create_point_address),
        addressSuggestionStatus = suggestion,
        acceptSuggestion = stringResource(Res.string.create_point_accept_suggestion),
        editAddress = stringResource(if (viewState.draft.address.isEmpty()) Res.string.create_point_enter_address else Res.string.create_point_edit_address),
        addressError = if (viewState.fieldError == CreatePointField.Address) stringResource(Res.string.create_point_address_invalid) else null,
        quotaTitle = stringResource(Res.string.create_point_quota),
        weekQuota = quota?.let { stringResource(Res.string.create_point_week_quota, it.weekUsed, it.weekLimit) },
        monthQuota = quota?.let { stringResource(Res.string.create_point_month_quota, it.monthUsed, it.monthLimit) },
        blockedUntil = viewState.blockedUntil?.let { stringResource(Res.string.create_point_blocked_body, formatLocalDateTime(it)) },
        quotaLoading = stringResource(Res.string.create_point_quota_loading),
        quotaError = stringResource(Res.string.create_point_quota_error),
        retry = stringResource(Res.string.create_point_retry),
        publish = stringResource(if (viewState.hasUncertainAttempt) Res.string.create_point_retry_publish else Res.string.create_point_publish),
        close = stringResource(Res.string.create_point_back),
        issue = issue,
    )
}


private fun formatCoordinate(value: Double): String = (round(value * 100_000) / 100_000).toString()
