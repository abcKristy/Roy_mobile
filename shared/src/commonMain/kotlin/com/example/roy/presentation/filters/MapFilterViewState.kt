package com.example.roy.presentation.filters

import com.example.roy.domain.points.OTHER_TOPIC_KEY
import com.example.roy.domain.points.TopicPreset
import com.example.roy.domain.points.validCustomTopicLabel

internal data class MapFilterViewState(
    val topicKey: String?,
    val customLabel: String,
    val peopleMin: String,
    val peopleMax: String,
    val presets: List<TopicPreset> = emptyList(),
    val isLoadingPresets: Boolean = true,
    val hasPresetError: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    val isRangeValid: Boolean
        get() {
            val min = peopleMin.toIntOrNull() ?: return false
            val max = peopleMax.toIntOrNull() ?: return false
            return min in 0..max && max <= 50
        }

    val isLabelValid: Boolean
        get() = topicKey != OTHER_TOPIC_KEY || customLabel.isBlank() || validCustomTopicLabel(customLabel)
}
