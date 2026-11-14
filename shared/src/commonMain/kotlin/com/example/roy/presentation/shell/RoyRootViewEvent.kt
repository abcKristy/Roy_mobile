package com.example.roy.presentation.shell

import com.example.roy.presentation.navigation.MainTab

internal sealed class RoyRootViewEvent {
    data object Logout : RoyRootViewEvent()
    data class OnTabSelected(val tab: MainTab) : RoyRootViewEvent()
}
