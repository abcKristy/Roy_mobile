package com.example.roy.presentation.points.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.roy.domain.map.PointTopic
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.map.mapTopicIcon
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.painterResource

internal data class PointDetailStrings(
    val back: String,
    val map: String,
    val loading: String,
    val gone: String,
    val empty: String,
    val totalPeople: String,
    val topicCounts: Map<String, String>,
    val description: String,
    val join: String,
    val open: String,
    val add: String,
    val leave: String,
    val leaveTitle: String,
    val leaveBody: String,
    val cancel: String,
    val retry: String,
    val selectExisting: String,
    val existing: String,
    val customLabel: String,
    val error: String?,
    val sheetError: String?,
)

@Composable
internal fun PointDetailView(
    viewState: PointDetailState,
    strings: PointDetailStrings,
    eventHandler: (PointDetailEvent) -> Unit,
) {
    val colors = RoyTheme.colors
    val typography = RoyTheme.typography
    val spacing = RoyTheme.dimensions.spacing
    val point = viewState.point
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        TertiaryButton(
            label = if (viewState.isGone) strings.map else strings.back,
            onClick = { eventHandler(PointDetailEvent.OnBack) },
            isEnabled = viewState.operation == null,
        )
        when {
            viewState.isGone -> BasicText(
                strings.gone,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                 },
                style = typography.headingMedium.copy(color = colors.textPrimary),
            )
            point != null -> {
                BasicText(
                    point.address,
                    modifier = Modifier.semantics { heading() },
                    style = typography.headingMedium.copy(color = colors.textPrimary),
                )
                BasicText(strings.totalPeople, style = typography.body.copy(color = colors.textSecondary))
                if (point.topics.isEmpty()) {
                    BasicText(strings.empty, style = typography.body.copy(color = colors.textSecondary))
                }
                for (topic in point.topics) {
                    TopicCard(topic, viewState, strings.topicCounts.getValue(topic.id), eventHandler)
                }
                viewState.selectedTopic?.let { topic ->
                    BasicText(strings.description, style = typography.body.copy(color = colors.textPrimary))
                    MembershipActions(topic, viewState, strings, eventHandler)
                }
                SecondaryButton(
                    text = strings.add,
                    onClick = { eventHandler(PointDetailEvent.OnAddRequested) },
                    modifier = Modifier.fillMaxWidth(),
                    isEnabled = !viewState.isBusy,
                )
            }
        }
        if (viewState.isLoading) {
            LoadingState(LoadingMode.Inline, strings.loading)
        }
        strings.error?.let { error ->
            ErrorState(error, !viewState.isBusy, { eventHandler(PointDetailEvent.OnRetry) })
        }
    }

}

@Composable
private fun TopicCard(
    topic: PointTopic,
    viewState: PointDetailState,
    count: String,
    eventHandler: (PointDetailEvent) -> Unit,
) {
    val typography = RoyTheme.typography
    val colors = RoyTheme.colors
    val spacing = RoyTheme.dimensions.spacing
    RoyCard(
        modifier = Modifier.fillMaxWidth(),
        isSelected = viewState.selectedTopicId == topic.id,
        onClick = if (viewState.isBusy) null else { { eventHandler(PointDetailEvent.OnTopicSelected(topic.id)) } },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            Image(
                painterResource(mapTopicIcon(topic.presetKey)),
                contentDescription = null,
                modifier = Modifier.size(RoyTheme.dimensions.iconSize),
                colorFilter = ColorFilter.tint(colors.textPrimary),
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                BasicText(topic.label, style = typography.headingSmall.copy(color = colors.textPrimary))
                BasicText(count, style = typography.bodySmall.copy(color = colors.textSecondary))
            }
        }
    }
}

@Composable
private fun MembershipActions(
    topic: PointTopic,
    viewState: PointDetailState,
    strings: PointDetailStrings,
    eventHandler: (PointDetailEvent) -> Unit,
) {
    val isMember = topic.chatId in viewState.memberChatIds
    PrimaryButton(
        text = if (isMember) strings.open else strings.join,
        onClick = { eventHandler(PointDetailEvent.OnJoinRequested) },
        modifier = Modifier.fillMaxWidth(),
        isEnabled = !viewState.isBusy && (!viewState.isAuthorized || viewState.isMembershipKnown || isMember),
        isSubmitting = viewState.operation == PointDetailOperation.Join,
    )
    if (isMember) {
        TertiaryButton(
            label = strings.leave,
            onClick = { eventHandler(PointDetailEvent.OnLeaveRequested) },
            modifier = Modifier.fillMaxWidth(),
            isEnabled = !viewState.isBusy,
        )
    }
}
