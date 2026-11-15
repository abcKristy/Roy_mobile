package com.example.roy.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.roy.presentation.components.feedback.EmptyCourtyardIllustration
import com.example.roy.presentation.components.feedback.EmptyState
import com.example.roy.presentation.components.feedback.EmptyStateAction
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.empty_chats_body
import roy.shared.generated.resources.empty_chats_open_map
import roy.shared.generated.resources.empty_chats_title

private val tabRoutes = mapOf(
    MainTab.Profile to RoyRoute.Profile,
    MainTab.Map to RoyRoute.Map,
    MainTab.Chats to RoyRoute.Chats,
)

internal fun NavDestination?.mainTab(): MainTab = tabRoutes.entries.firstOrNull { (_, route) ->
    this?.hasRoute(route::class) == true
}?.key ?: MainTab.Map

internal fun NavHostController.openTab(tab: MainTab) {
    if (currentDestination?.hasRoute(RoyRoute.PointDetails::class) == true && tab == MainTab.Map) {
        popBackStack()
        return
    }
    if (currentDestination?.hasRoute(tabRoutes.getValue(tab)::class) == true) return
    navigate(tabRoutes.getValue(tab)) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) { saveState = true }
    }
}

@Composable
internal fun RoyNavHost(
    navController: NavHostController,
    profile: @Composable () -> Unit,
    map: @Composable () -> Unit,
    pointDetails: @Composable (String) -> Unit,
    protectedAuth: @Composable (Long) -> Unit,
    protectedTarget: @Composable () -> Unit,
    onOpenMap: () -> Unit,
) {
    val latestProfile by rememberUpdatedState(profile)
    val latestMap by rememberUpdatedState(map)
    val latestPointDetails by rememberUpdatedState(pointDetails)
    val latestAuth by rememberUpdatedState(protectedAuth)
    val latestTarget by rememberUpdatedState(protectedTarget)
    val latestOpenMap by rememberUpdatedState(onOpenMap)
    NavHost(navController = navController, startDestination = RoyRoute.Map, modifier = Modifier.fillMaxSize()) {
        composable<RoyRoute.Map> { latestMap() }
        composable<RoyRoute.PointDetails> { entry ->
            latestPointDetails(entry.toRoute<RoyRoute.PointDetails>().pointId)
        }
        composable<RoyRoute.ProtectedAuth> { latestAuth(it.toRoute<RoyRoute.ProtectedAuth>().flowId) }
        composable<RoyRoute.ProtectedTarget> { latestTarget() }
        composable<RoyRoute.Profile> { latestProfile() }
        composable<RoyRoute.Chats> {
            Box(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(RoyTheme.dimensions.spacing.medium),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(Res.string.empty_chats_title),
                    body = stringResource(Res.string.empty_chats_body),
                    modifier = Modifier.widthIn(max = 360.dp),
                    illustration = { EmptyCourtyardIllustration() },
                    action = EmptyStateAction(stringResource(Res.string.empty_chats_open_map), latestOpenMap),
                )
            }
        }
    }
}
