package com.example.roy.presentation.navigation

import com.example.roy.domain.map.Position
import com.example.roy.MeetingNotificationTarget

internal sealed interface PendingProtectedIntent {
    data class CreatePoint(val position: Position) : PendingProtectedIntent
    data class AddTopic(val pointId: String) : PendingProtectedIntent
    data class OpenTopic(val pointId: String, val topicId: String) : PendingProtectedIntent
    data class OpenMeeting(val target: MeetingNotificationTarget) : PendingProtectedIntent
}
