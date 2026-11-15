package com.example.roy.presentation.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.location.DeviceLocation
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.map.LoadViewport
import com.example.roy.domain.map.PointRepository
import com.example.roy.domain.navigation.ResolveProtectedTarget
import com.example.roy.presentation.auth.AuthNavHost
import com.example.roy.presentation.map.MapScreen
import com.example.roy.presentation.map.MapViewModel
import com.example.roy.presentation.navigation.RoyRoute
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.components.overlays.RoySnackbarMessage
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.navigation.MainTab
import com.example.roy.presentation.navigation.AuthReturnCoordinator
import com.example.roy.presentation.navigation.PendingProtectedIntent
import com.example.roy.presentation.navigation.ProtectedNavigation
import com.example.roy.presentation.navigation.ProtectedTargetScreen
import com.example.roy.presentation.navigation.PublicPointTopicsScreen
import com.example.roy.presentation.navigation.RoyNavHost
import com.example.roy.presentation.navigation.mainTab
import com.example.roy.presentation.navigation.openTab
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_point_gone

@Composable
internal fun RoyRootScreen(
    session: SessionCoordinator,
    points: PointRepository,
    location: DeviceLocation,
    sessionStatus: SessionStatus,
    continuation: AuthReturnCoordinator,
    resolver: ResolveProtectedTarget,
    guestProfile: (@Composable () -> Unit)? = null,
) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val viewModel = viewModel(viewModelStoreOwner = owner) { RoyRootViewModel(session) }
    val operations by viewModel.viewState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbar = remember { RoySnackbarHostState() }
    var snackbarMessageId by remember { mutableLongStateOf(0L) }
    var target by remember { mutableStateOf<ProtectedNavigation.Continue?>(null) }
    val expiration by session.expiration.collectAsStateWithLifecycle()
    val loadPoint = remember(points) { LoadPoint(points) }
    val goneMessage = stringResource(Res.string.map_point_gone)
    val eventHandler: (RoyRootViewEvent) -> Unit = remember(viewModel) { viewModel::handleEvent }

    fun navigate(effect: ProtectedNavigation?) {
        when (effect) {
            null -> Unit
            is ProtectedNavigation.OpenAuth -> {
                if (navController.currentDestination?.hasRoute<RoyRoute.ProtectedAuth>() == true ||
                    navController.currentDestination?.hasRoute<RoyRoute.ProtectedTarget>() == true
                ) navController.popBackStack()
                navController.navigate(RoyRoute.ProtectedAuth(effect.flowId)) { launchSingleTop = true }
            }
            is ProtectedNavigation.Continue -> {
                if ((session.state.value as? SessionStatus.Active)?.scopeId != effect.scopeId) return
                target = effect
                navController.navigate(RoyRoute.ProtectedTarget) { launchSingleTop = true }
            }
        }
    }

    fun request(intent: PendingProtectedIntent) {
        val current = navController.currentDestination
        if (current?.hasRoute<RoyRoute.ProtectedTarget>() == true) return
        navigate(continuation.request(intent, session.state.value))
    }

    LaunchedEffect(sessionStatus, expiration) {
        val effect = continuation.onSessionChanged(sessionStatus)
        if (sessionStatus == SessionStatus.Guest) {
            navigate(continuation.onSessionExpired(expiration) ?: effect)
        } else {
            navigate(effect)
        }
    }

    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            is RoyRootViewAction.OpenTab -> {
                val current = navController.currentDestination
                if (current?.hasRoute<RoyRoute.ProtectedAuth>() != true &&
                    current?.hasRoute<RoyRoute.ProtectedTarget>() != true
                ) navController.openTab(action.tab)
            }
        }
    }

    RoyShellLayout(
        snackbar = snackbar,
        tabs = {
            val destination = backStackEntry?.destination
            if (destination?.hasRoute<RoyRoute.ProtectedAuth>() != true &&
                destination?.hasRoute<RoyRoute.ProtectedTarget>() != true
            ) {
                RoyRootView(
                    viewState = RoyRootViewState(destination.mainTab()),
                    eventHandler = eventHandler,
                )
            }
        },
        content = {
            RoyNavHost(
                navController = navController,
                profile = {
                    when {
                        guestProfile != null -> guestProfile()
                        sessionStatus == SessionStatus.Guest -> AuthNavHost(session)
                        else -> ProfileSessionView(operations, eventHandler)
                    }
                },
                map = {
                    val mapModel = viewModel { MapViewModel(LoadViewport(points), loadPoint, location) }
                    MapScreen(
                        viewModel = mapModel,
                        snackbar = snackbar,
                        nextMessageId = { ++snackbarMessageId },
                        onOpenDetails = { pointId ->
                            if (navController.currentDestination?.hasRoute<RoyRoute.Map>() == true) {
                                navController.navigate(RoyRoute.PointDetails(pointId)) { launchSingleTop = true }
                            }
                        },
                        onCreatePoint = { request(PendingProtectedIntent.CreatePoint(it)) },
                    )
                },
                pointDetails = {
                    PublicPointTopicsScreen(it, loadPoint, { navController.popBackStack() }, ::request)
                },
                protectedAuth = { flowId ->
                    if (continuation.isAuthOpen(flowId)) {
                        AuthNavHost(
                            session,
                            onCancel = {
                                if (continuation.cancelAuth(flowId)) navController.popBackStack()
                            },
                            onSucceeded = {
                                val active = session.state.value as? SessionStatus.Active
                                if (active != null && continuation.isAuthOpen(flowId)) {
                                    val transition = continuation.authSucceeded(flowId, active)
                                    navController.popBackStack()
                                    navigate(transition)
                                }
                            },
                        )
                    } else {
                        LaunchedEffect(flowId) {
                            val entry = navController.currentBackStackEntry
                            if (entry?.destination?.hasRoute<RoyRoute.ProtectedAuth>() == true &&
                                entry.toRoute<RoyRoute.ProtectedAuth>().flowId == flowId
                            ) {
                                navController.popBackStack<RoyRoute.Map>(inclusive = false)
                            }
                        }
                    }
                },
                protectedTarget = {
                    val current = target
                    if (current != null && (sessionStatus as? SessionStatus.Active)?.scopeId == current.scopeId) {
                        ProtectedTargetScreen(
                            current,
                            resolver,
                            onBack = { navController.popBackStack() },
                            onGone = {
                                navController.popBackStack<RoyRoute.Map>(inclusive = false)
                                snackbar.show(RoySnackbarMessage(++snackbarMessageId, goneMessage))
                            },
                        )
                    } else {
                        LaunchedEffect(sessionStatus) {
                            if (navController.currentDestination?.hasRoute<RoyRoute.ProtectedTarget>() == true) {
                                navController.popBackStack<RoyRoute.Map>(inclusive = false)
                            }
                        }
                    }
                },
            ) {
                eventHandler(RoyRootViewEvent.OnTabSelected(MainTab.Map))
            }
        },
    )
}
