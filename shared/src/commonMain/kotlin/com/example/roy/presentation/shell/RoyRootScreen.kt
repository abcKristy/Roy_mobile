package com.example.roy.presentation.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.navigation.MainTab
import com.example.roy.presentation.navigation.RoyNavHost
import com.example.roy.presentation.navigation.mainTab
import com.example.roy.presentation.navigation.openTab

@Composable
internal fun RoyRootScreen() {
    val viewModel = viewModel { RoyRootViewModel() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbar = remember { RoySnackbarHostState() }
    val eventHandler: (RoyRootViewEvent) -> Unit = remember(viewModel) { viewModel::handleEvent }

    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            is RoyRootViewAction.OpenTab -> navController.openTab(action.tab)
        }
    }

    RoyShellLayout(
        snackbar = snackbar,
        tabs = {
            RoyRootView(
                viewState = RoyRootViewState(backStackEntry?.destination.mainTab()),
                eventHandler = eventHandler,
            )
        },
        content = {
            RoyNavHost(navController = navController) {
                eventHandler(RoyRootViewEvent.OnTabSelected(MainTab.Map))
            }
        },
    )
}
