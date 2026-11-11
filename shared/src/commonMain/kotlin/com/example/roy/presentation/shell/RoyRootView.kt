package com.example.roy.presentation.shell

import androidx.compose.runtime.Composable
import com.example.roy.presentation.navigation.MainTabs

@Composable
internal fun RoyRootView(viewState: RoyRootViewState, eventHandler: (RoyRootViewEvent) -> Unit) {
    MainTabs(
        selectedTab = viewState.selectedTab,
        onTabSelected = { tab -> eventHandler(RoyRootViewEvent.OnTabSelected(tab)) },
    )
}
