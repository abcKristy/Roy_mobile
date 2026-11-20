package com.example.roy.presentation.shell

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.presentation.core.BaseViewModel
import com.example.roy.presentation.navigation.MainTab
import kotlinx.coroutines.launch

internal data class RoyRootOperations(
    val isLoggingOut: Boolean = false,
    val logoutError: AuthErrorCode? = null,
)

internal class RoyRootViewModel(private val session: SessionCoordinator) :
    BaseViewModel<RoyRootOperations, RoyRootViewEvent, RoyRootViewAction>(RoyRootOperations()) {
    override fun handleEvent(event: RoyRootViewEvent) {
        when (event) {
            is RoyRootViewEvent.OnTabSelected -> onTabSelected(event.tab)
            RoyRootViewEvent.OnLogout -> onLogout()
        }
    }

    private fun onTabSelected(tab: MainTab) {
        viewModelScope.launch { sendAction(RoyRootViewAction.OpenTab(tab)) }
    }

    private fun onLogout() {
        if (viewState.value.isLoggingOut) return
        updateState { RoyRootOperations(isLoggingOut = true) }
        viewModelScope.launch {
            val result = session.logout()
            val error = (result as? AuthResult.Failure)?.error?.code
            updateState { RoyRootOperations(logoutError = error) }
        }
    }
}
