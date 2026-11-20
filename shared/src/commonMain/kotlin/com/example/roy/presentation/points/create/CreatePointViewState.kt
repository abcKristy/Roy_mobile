package com.example.roy.presentation.points.create

import com.example.roy.domain.points.CreatePointDraft
import com.example.roy.domain.points.CreatePointField
import com.example.roy.domain.points.CreationContext
import com.example.roy.domain.points.TopicPreset
import kotlin.time.Instant

internal data class CreatePointViewState(
    val draft: CreatePointDraft,
    val presets: List<TopicPreset> = emptyList(),
    val isLoadingPresets: Boolean = true,
    val hasPresetError: Boolean = false,
    val suggestionStatus: AddressSuggestionStatus = AddressSuggestionStatus.Loading,
    val suggestion: String? = null,
    val context: CreationContext? = null,
    val isLoadingContext: Boolean = true,
    val hasContextError: Boolean = false,
    val blockedUntil: Instant? = null,
    val isSubmitting: Boolean = false,
    val isCreated: Boolean = false,
    val hasUncertainAttempt: Boolean = false,
    val fieldError: CreatePointField? = null,
    val issue: CreatePointIssue? = null,
    val dialog: CreatePointDialog? = null,
    val canPublish: Boolean = false,
)
