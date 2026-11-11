package com.example.roy.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
internal sealed interface RoyRoute {
    @Serializable data object Profile : RoyRoute
    @Serializable data object Map : RoyRoute
    @Serializable data object Chats : RoyRoute
}
