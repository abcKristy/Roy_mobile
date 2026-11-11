package com.example.roy.presentation.shell

import androidx.lifecycle.viewModelScope
import com.example.roy.presentation.core.BaseViewModel
import com.example.roy.presentation.navigation.MainTab
import kotlinx.coroutines.launch

// The NavHost owns selected-tab state so restoration uses its actual back stack.
internal class RoyRootViewModel : BaseViewModel<Unit, RoyRootViewEvent, RoyRootViewAction>(Unit) {
    override fun handleEvent(event: RoyRootViewEvent) {
        when (event) {
            is RoyRootViewEvent.OnTabSelected -> onTabSelected(event.tab)
        }
    }

    private fun onTabSelected(tab: MainTab) {
        viewModelScope.launch { sendAction(RoyRootViewAction.OpenTab(tab)) }
    }
}
