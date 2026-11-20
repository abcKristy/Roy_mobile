package com.example.roy.presentation.chat


internal data class ChatReportState(
    val reason: String = "",
    val isSubmitting: Boolean = false,
    val feedback: ChatReportFeedback? = null,
    val isOnline: Boolean = true,
) {
    val isFinal: Boolean
        get() = feedback == ChatReportFeedback.Submitted || feedback == ChatReportFeedback.Duplicate ||
            feedback == ChatReportFeedback.Forbidden

    val isReasonValid: Boolean get() = isReportReasonValid(reason)
    val canSubmit: Boolean get() = isOnline && !isSubmitting && !isFinal && isReasonValid
}
