package com.example.roy.presentation.shell

import com.example.roy.presentation.navigation.MainTab

internal sealed class RoyRootViewAction {
    data class OpenTab(val tab: MainTab) : RoyRootViewAction()
}
