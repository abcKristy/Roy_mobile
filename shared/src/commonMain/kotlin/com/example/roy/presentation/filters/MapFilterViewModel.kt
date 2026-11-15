package com.example.roy.presentation.filters

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.points.OtherTopicKey
import com.example.roy.domain.points.TopicPreset
import com.example.roy.domain.points.validCustomTopicLabel
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.launch

internal data class MapFilterViewState(
    val topicKey: String?,
    val customLabel: String,
    val peopleMin: String,
    val peopleMax: String,
    val presets: List<TopicPreset> = emptyList(),
    val loadingPresets: Boolean = true,
    val presetError: Boolean = false,
    val submitting: Boolean = false,
) {
    val rangeValid: Boolean
        get() {
            val min = peopleMin.toIntOrNull() ?: return false
            val max = peopleMax.toIntOrNull() ?: return false
            return min in 0..max && max <= 50
        }

    val labelValid: Boolean
        get() = topicKey != OtherTopicKey || customLabel.isBlank() || validCustomTopicLabel(customLabel)
}

internal sealed class MapFilterEvent {
    data class Topic(val key: String?) : MapFilterEvent()
    data class CustomLabel(val label: String) : MapFilterEvent()
    data class PeopleMin(val value: String) : MapFilterEvent()
    data class PeopleMax(val value: String) : MapFilterEvent()
    data object RetryPresets : MapFilterEvent()
    data object Apply : MapFilterEvent()
    data object Reset : MapFilterEvent()
    data object Back : MapFilterEvent()
}

internal sealed class MapFilterAction {
    data class Applied(val filter: PointFilter) : MapFilterAction()
    data object Back : MapFilterAction()
}

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
        if (viewState.value.submitting) return
        when (event) {
            is MapFilterEvent.Topic -> updateState { it.copy(topicKey = event.key) }
            is MapFilterEvent.CustomLabel -> updateState { it.copy(customLabel = event.label) }
            is MapFilterEvent.PeopleMin -> updateState { it.copy(peopleMin = event.value) }
            is MapFilterEvent.PeopleMax -> updateState { it.copy(peopleMax = event.value) }
            MapFilterEvent.RetryPresets -> if (!viewState.value.loadingPresets) fetchPresets()
            MapFilterEvent.Reset -> submit(PointFilter())
            MapFilterEvent.Back -> viewModelScope.launch { sendAction(MapFilterAction.Back) }
            MapFilterEvent.Apply -> {
                val state = viewState.value
                if (!state.rangeValid || !state.labelValid) return
                submit(
                    PointFilter(
                        topicKey = state.topicKey,
                        customTopicLabel = state.customLabel.trim()
                            .takeIf { state.topicKey == OtherTopicKey && it.isNotEmpty() },
                        peopleMin = state.peopleMin.toInt().takeUnless { it == 0 },
                        peopleMax = state.peopleMax.toInt().takeUnless { it == 50 },
                    ),
                )
            }
        }
    }

    private fun fetchPresets() {
        updateState { it.copy(loadingPresets = true, presetError = false) }
        viewModelScope.launch {
            when (val result = loadPresets()) {
                is AuthResult.Success -> updateState { it.copy(loadingPresets = false, presets = result.value) }
                is AuthResult.Failure -> updateState { it.copy(loadingPresets = false, presetError = true) }
            }
        }
    }

    private fun submit(filter: PointFilter) {
        updateState { it.copy(submitting = true) }
        viewModelScope.launch { sendAction(MapFilterAction.Applied(filter)) }
    }
}
