package com.example.roy.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.Position
import com.example.roy.domain.navigation.DraftContext
import com.example.roy.domain.navigation.ProtectedTargetResult
import com.example.roy.domain.navigation.ResolveProtectedTarget
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.components.fields.RoyTextField
import com.example.roy.presentation.core.BaseViewModel
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.theme.RoyTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.*

@Composable
internal fun PublicPointTopicsScreen(
    pointId: String,
    loadPoint: LoadPoint,
    onBack: () -> Unit,
    onOpenTopic: (PendingProtectedIntent.OpenTopic) -> Unit,
) {
    val model = viewModel { DestinationLoader { loadPoint(pointId) } }
    val state by model.viewState.collectAsStateWithLifecycle()
    DestinationContent {
        TertiaryButton(stringResource(Res.string.protected_back), onBack)
        when (val result = state.result) {
            null -> LoadingState(LoadingMode.Inline, stringResource(Res.string.map_detail_loading))
            is PointResult.Failure -> ErrorState(
                safeMessage = stringResource(
                    if (result.error == PointError.ResourceGone) Res.string.map_point_gone else Res.string.protected_unavailable,
                ),
                isRetryEnabled = true,
                onRetry = { model.handleEvent(DestinationEvent.Retry) },
            )
            is PointResult.Success<Point> -> {
                val point = result.value
                DestinationHeading(point.address)
                val singleTopic = point.topics.size == 1
                point.topics.forEach { topic ->
                    val label = if (singleTopic) {
                        stringResource(Res.string.protected_open_chat)
                    } else {
                        stringResource(Res.string.protected_open_topic, topic.label)
                    }
                    TertiaryButton(label, { onOpenTopic(PendingProtectedIntent.OpenTopic(point.id, topic.id)) })
                }
            }
        }
    }
}

@Composable
internal fun ProtectedTargetScreen(
    target: ProtectedNavigation.Continue,
    resolver: ResolveProtectedTarget,
    onBack: () -> Unit,
    onGone: () -> Unit,
) {
    val model = viewModel {
        DestinationLoader {
            when (val intent = target.intent) {
                is PendingProtectedIntent.CreatePoint -> resolver.draft(intent.position, target.scopeId)
                    .mapReady { PreparedContinuation.Draft(intent.position, it) }
                is PendingProtectedIntent.OpenTopic -> resolver.topic(intent.pointId, intent.topicId, target.scopeId)
                    .mapReady { PreparedContinuation.Topic(it) }
            }
        }
    }
    val state by model.viewState.collectAsStateWithLifecycle()
    CollectMviActions(model.viewAction) { onGone() }
    val draftForm = if (target.intent is PendingProtectedIntent.CreatePoint) {
        viewModel { PreparedDraftViewModel() }
    } else null
    val formState = draftForm?.viewState?.collectAsStateWithLifecycle()?.value
    ProtectedTargetView(
        state.result,
        onBack,
        onRetry = { model.handleEvent(DestinationEvent.Retry) },
        draftAddress = formState?.address.orEmpty(),
        onDraftAddressChanged = { draftForm?.handleEvent(DraftAddressChanged(it)) },
    )
}

@Composable
internal fun ProtectedTargetView(
    result: ProtectedTargetResult<PreparedContinuation>?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    draftAddress: String = "",
    onDraftAddressChanged: (String) -> Unit = {},
) {
    DestinationContent {
        TertiaryButton(stringResource(Res.string.protected_back), onBack)
        when (result) {
            null -> LoadingState(LoadingMode.Inline, stringResource(Res.string.protected_loading))
            is ProtectedTargetResult.Ready -> when (val destination = result.value) {
                is PreparedContinuation.Draft -> DraftHandoff(
                    destination.position,
                    destination.context,
                    draftAddress,
                    onDraftAddressChanged,
                )
                is PreparedContinuation.Topic -> DestinationHeading(stringResource(Res.string.protected_topic_prepared))
            }
            ProtectedTargetResult.Gone -> Unit
            ProtectedTargetResult.Forbidden -> DestinationHeading(stringResource(Res.string.protected_forbidden))
            ProtectedTargetResult.Unavailable -> ErrorState(
                safeMessage = stringResource(Res.string.protected_unavailable),
                isRetryEnabled = true,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun DraftHandoff(
    position: Position,
    context: DraftContext,
    address: String,
    onAddressChanged: (String) -> Unit,
) {
    DestinationHeading(stringResource(Res.string.protected_draft))
    DestinationText(
        stringResource(Res.string.protected_coordinates, position.latitude.toString(), position.longitude.toString()),
    )
    DestinationText(stringResource(Res.string.protected_quota, context.weekRemaining, context.monthRemaining))
    RoyTextField(address, stringResource(Res.string.protected_draft_address), onAddressChanged)
    if (context.nearbyAddresses.isNotEmpty()) {
        DestinationHeading(stringResource(Res.string.protected_nearby))
        context.nearbyAddresses.forEach { DestinationText(it) }
    }
    DestinationText(stringResource(Res.string.protected_draft_prepared))
}

@Composable
private fun DestinationContent(content: @Composable () -> Unit) {
    val spacing = RoyTheme.dimensions.spacing
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) { content() }
}

@Composable
private fun DestinationHeading(text: String) {
    BasicText(
        text,
        modifier = Modifier.semantics { heading() },
        style = RoyTheme.typography.headingMedium.copy(color = RoyTheme.colors.textPrimary),
    )
}

@Composable
private fun DestinationText(text: String) {
    BasicText(text, style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.textPrimary))
}

internal sealed interface PreparedContinuation {
    data class Draft(val position: Position, val context: DraftContext) : PreparedContinuation
    data class Topic(val chatId: String) : PreparedContinuation
}

private inline fun <T, R> ProtectedTargetResult<T>.mapReady(transform: (T) -> R): ProtectedTargetResult<R> =
    when (this) {
        is ProtectedTargetResult.Ready -> ProtectedTargetResult.Ready(transform(value))
        ProtectedTargetResult.Gone -> ProtectedTargetResult.Gone
        ProtectedTargetResult.Forbidden -> ProtectedTargetResult.Forbidden
        ProtectedTargetResult.Unavailable -> ProtectedTargetResult.Unavailable
    }

private data class PreparedDraftState(val address: String = "")
private data class DraftAddressChanged(val address: String)

private class PreparedDraftViewModel :
    BaseViewModel<PreparedDraftState, DraftAddressChanged, Nothing>(PreparedDraftState()) {
    override fun handleEvent(event: DraftAddressChanged) {
        updateState { it.copy(address = event.address) }
    }
}

private data class DestinationState<T>(val result: T? = null)
private enum class DestinationEvent { Retry }
private enum class DestinationAction { ReturnToMap }

private class DestinationLoader<T : Any>(private val load: suspend () -> T) :
    BaseViewModel<DestinationState<T>, DestinationEvent, DestinationAction>(DestinationState()) {
    private var job: Job? = null

    init {
        reload()
    }

    override fun handleEvent(event: DestinationEvent) {
        when (event) {
            DestinationEvent.Retry -> reload()
        }
    }

    private fun reload() {
        if (job?.isActive == true) return
        updateState { DestinationState() }
        job = viewModelScope.launch {
            val result = load()
            updateState { DestinationState(result) }
            if (result == ProtectedTargetResult.Gone) sendAction(DestinationAction.ReturnToMap)
        }
    }
}
