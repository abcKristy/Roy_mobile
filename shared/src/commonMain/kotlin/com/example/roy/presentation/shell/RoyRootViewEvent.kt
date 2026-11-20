package com.example.roy.presentation.shell

import com.example.roy.presentation.navigation.MainTab

internal sealed class RoyRootViewEvent {
    data object OnLogout : RoyRootViewEvent()
    data class OnTabSelected(val tab: MainTab) : RoyRootViewEvent()
}
