package com.example.roy.presentation.filters

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.points.OTHER_TOPIC_KEY
import com.example.roy.domain.points.TopicPreset
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.launch

internal class MapFilterViewModel(
    applied: PointFilter,
    private val loadPresets: suspend () -> AuthResult<List<TopicPreset>>,
) : BaseViewModel<MapFilterViewState, MapFilterEvent, MapFilterAction>(
    MapFilterViewState(
        topicKey = applied.topicKey,
        customLabel = applied.customTopicLabel.orEmpty(),
        peopleMin = (applied.peopleMin ?: 0).toString(),
        peopleMax = (applied.peopleMax ?: 50).toString(),
    ),
) {
    init {
        fetchPresets()
    }

    override fun handleEvent(event: MapFilterEvent) {
        if (viewState.value.isSubmitting) return
        when (event) {
            is MapFilterEvent.OnTopic -> onTopic(event)
            is MapFilterEvent.OnCustomLabel -> onCustomLabel(event)
            is MapFilterEvent.OnPeopleMin -> onPeopleMin(event)
            is MapFilterEvent.OnPeopleMax -> onPeopleMax(event)
            MapFilterEvent.OnRetryPresets -> onRetryPresets()
            MapFilterEvent.OnReset -> onReset()
            MapFilterEvent.OnBack -> onBack()
            MapFilterEvent.OnApply -> onApply()
        }
    }

    private fun onTopic(event: MapFilterEvent.OnTopic) {
        updateState { it.copy(topicKey = event.key) }
    }

    private fun onCustomLabel(event: MapFilterEvent.OnCustomLabel) {
        updateState { it.copy(customLabel = event.label) }
    }

    private fun onPeopleMin(event: MapFilterEvent.OnPeopleMin) {
        updateState { it.copy(peopleMin = event.value) }
    }

    private fun onPeopleMax(event: MapFilterEvent.OnPeopleMax) {
        updateState { it.copy(peopleMax = event.value) }
    }

    private fun onRetryPresets() {
        if (!viewState.value.isLoadingPresets) fetchPresets()
    }

    private fun onReset() {
        submit(PointFilter())
    }

    private fun onBack() {
        viewModelScope.launch { sendAction(MapFilterAction.Back) }
    }

    private fun onApply() {
        val state = viewState.value
        if (!state.isRangeValid || !state.isLabelValid) return
        submit(
            PointFilter(
                topicKey = state.topicKey,
                customTopicLabel = state.customLabel.trim()
                    .takeIf { state.topicKey == OTHER_TOPIC_KEY && it.isNotEmpty() },
                peopleMin = state.peopleMin.toInt().takeUnless { it == 0 },
                peopleMax = state.peopleMax.toInt().takeUnless { it == 50 },
            ),
        )
    }

    private fun fetchPresets() {
        updateState { it.copy(isLoadingPresets = true, hasPresetError = false) }
        viewModelScope.launch {
            when (val result = loadPresets()) {
                is AuthResult.Success -> updateState { it.copy(isLoadingPresets = false, presets = result.value) }
                is AuthResult.Failure -> updateState { it.copy(isLoadingPresets = false, hasPresetError = true) }
            }
        }
    }

    private fun submit(filter: PointFilter) {
        updateState { it.copy(isSubmitting = true) }
        viewModelScope.launch { sendAction(MapFilterAction.Applied(filter)) }
    }
}
