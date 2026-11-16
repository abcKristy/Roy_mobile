package com.example.roy.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
internal sealed interface RoyRoute {
    @Serializable data object Login : RoyRoute
    @Serializable data object Register : RoyRoute
    @Serializable data object Profile : RoyRoute
    @Serializable data object Map : RoyRoute
    @Serializable data object MapFilters : RoyRoute
    @Serializable data object Chats : RoyRoute
    @Serializable data class Notifications(val settings: Boolean = false) : RoyRoute
    @Serializable
    data class PointDetails(
        val pointId: String,
        val topicId: String? = null,
        val offerAddTopic: Boolean = false,
    ) : RoyRoute
    @Serializable
    data class TopicChat(
        val pointId: String,
        val topicId: String,
        val chatId: String,
        val isMember: Boolean,
        val scopeId: Long,
        val proposalId: String? = null,
    ) : RoyRoute
    @Serializable data class ProtectedAuth(val flowId: Long) : RoyRoute
    @Serializable data object ProtectedTarget : RoyRoute
}
