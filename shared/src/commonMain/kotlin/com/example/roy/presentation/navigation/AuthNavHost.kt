package com.example.roy.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.roy.domain.auth.AuthMode
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.presentation.auth.AuthScreen

@Composable
internal fun AuthNavHost(
    session: SessionCoordinator,
    onCancel: (() -> Unit)? = null,
    onSucceeded: () -> Unit = {},
) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = RoyRoute.Login) {
        composable<RoyRoute.Login> {
            AuthScreen(AuthMode.Login, session, onCancel, onSucceeded) {
                if (nav.currentDestination?.hasRoute<RoyRoute.Login>() == true) {
                    nav.navigate(RoyRoute.Register) { launchSingleTop = true }
                }
            }
        }
        composable<RoyRoute.Register> {
            AuthScreen(AuthMode.Register, session, onCancel, onSucceeded) {
                if (nav.currentDestination?.hasRoute<RoyRoute.Register>() == true) {
                    nav.popBackStack<RoyRoute.Login>(inclusive = false)
                }
            }
        }
    }
}
