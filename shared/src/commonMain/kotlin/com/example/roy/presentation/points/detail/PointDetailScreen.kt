package com.example.roy.presentation.points.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.presentation.components.overlays.RoyDialog
import com.example.roy.presentation.core.CollectMviActions
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.feedback_retry
import roy.shared.generated.resources.map_detail_loading
import roy.shared.generated.resources.map_point_gone
import roy.shared.generated.resources.point_detail_add
import roy.shared.generated.resources.point_detail_cancel
import roy.shared.generated.resources.point_detail_custom_label
import roy.shared.generated.resources.point_detail_description
import roy.shared.generated.resources.point_detail_duplicate
import roy.shared.generated.resources.point_detail_empty
import roy.shared.generated.resources.point_detail_existing
import roy.shared.generated.resources.point_detail_invalid_topic
import roy.shared.generated.resources.point_detail_join
import roy.shared.generated.resources.point_detail_leave
import roy.shared.generated.resources.point_detail_leave_body
import roy.shared.generated.resources.point_detail_leave_title
import roy.shared.generated.resources.point_detail_network
import roy.shared.generated.resources.point_detail_people
import roy.shared.generated.resources.point_detail_select_existing
import roy.shared.generated.resources.point_detail_session
import roy.shared.generated.resources.point_detail_to_map
import roy.shared.generated.resources.protected_back
import roy.shared.generated.resources.protected_open_chat
import roy.shared.generated.resources.protected_unavailable

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PointDetailScreen(
    viewModel: PointDetailViewModel,
    onBack: () -> Unit,
    onRequireJoin: (pointId: String, topicId: String, chatId: String) -> Unit,
    onRequireAddTopic: (pointId: String) -> Unit,
    onOpenChat: (pointId: String, topicId: String, chatId: String) -> Unit,
    onPointChanged: (pointId: String, isGone: Boolean) -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(viewModel) { viewModel::handleEvent }
    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            PointDetailAction.Back -> onBack()
            is PointDetailAction.RequireJoin -> onRequireJoin(action.pointId, action.topicId, action.chatId)
            is PointDetailAction.RequireAdd -> onRequireAddTopic(action.pointId)
            is PointDetailAction.OpenChat -> onOpenChat(action.pointId, action.topicId, action.chatId)
            is PointDetailAction.PointChanged -> onPointChanged(action.pointId, action.isGone)
        }
    }

    BackHandler(enabled = !viewState.isAddSheetOpen && !viewState.isLeaveDialogOpen) {
        eventHandler(PointDetailEvent.OnBack)
    }
    val strings = pointDetailStrings(viewState)
    PointDetailView(viewState, strings, eventHandler)
    if (viewState.isAddSheetOpen) AddTopicSheet(viewState, strings, eventHandler)
    if (viewState.isLeaveDialogOpen) {
        RoyDialog(
            title = strings.leaveTitle,
            body = strings.leaveBody,
            confirmText = strings.leave,
            cancelText = strings.cancel,
            onConfirm = { eventHandler(PointDetailEvent.OnLeaveConfirmed) },
            onCancel = { eventHandler(PointDetailEvent.OnDialogDismissed) },
            isSubmitting = viewState.operation == PointDetailOperation.Leave,
            isDestructive = true,
        )
    }
}

@Composable
internal fun pointDetailStrings(viewState: PointDetailState) = PointDetailStrings(
    back = stringResource(Res.string.protected_back),
    map = stringResource(Res.string.point_detail_to_map),
    loading = stringResource(Res.string.map_detail_loading),
    gone = stringResource(Res.string.map_point_gone),
    empty = stringResource(Res.string.point_detail_empty),
    totalPeople = viewState.point?.peopleCount?.let { stringResource(Res.string.point_detail_people, it) }.orEmpty(),
    topicCounts = viewState.point?.topics.orEmpty().associate { topic ->
        topic.id to stringResource(Res.string.point_detail_people, topic.participantCount)
    },
    description = viewState.selectedTopic?.label?.let { stringResource(Res.string.point_detail_description, it) }.orEmpty(),
    join = stringResource(Res.string.point_detail_join),
    open = stringResource(Res.string.protected_open_chat),
    add = stringResource(Res.string.point_detail_add),
    leave = stringResource(Res.string.point_detail_leave),
    leaveTitle = stringResource(Res.string.point_detail_leave_title),
    leaveBody = stringResource(Res.string.point_detail_leave_body),
    cancel = stringResource(Res.string.point_detail_cancel),
    retry = stringResource(Res.string.feedback_retry),
    selectExisting = stringResource(Res.string.point_detail_select_existing),
    existing = stringResource(Res.string.point_detail_existing),
    customLabel = stringResource(Res.string.point_detail_custom_label),
    error = viewState.error?.displayText(),
    sheetError = viewState.sheetError?.displayText(),
)

@Composable
private fun PointDetailError.displayText() = stringResource(
    when (this) {
        PointDetailError.Network -> Res.string.point_detail_network
        PointDetailError.Unavailable -> Res.string.protected_unavailable
        PointDetailError.Session -> Res.string.point_detail_session
        PointDetailError.Duplicate -> Res.string.point_detail_duplicate
        PointDetailError.InvalidTopic -> Res.string.point_detail_invalid_topic
    },
)
