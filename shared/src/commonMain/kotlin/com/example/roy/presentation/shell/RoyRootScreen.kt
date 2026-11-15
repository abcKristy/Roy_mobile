package com.example.roy.presentation.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.roy.domain.map.PointFilterStore
import com.example.roy.domain.points.CreatePointRepository
import com.example.roy.domain.points.CreatePointUseCase
import com.example.roy.domain.points.PointDetailOperations
import com.example.roy.presentation.auth.AuthNavHost
import com.example.roy.presentation.filters.MapFilterScreen
import com.example.roy.presentation.filters.MapFilterViewModel
import com.example.roy.presentation.map.MapScreen
import com.example.roy.presentation.map.MapViewModel
import com.example.roy.presentation.map.MapViewEvent
import com.example.roy.presentation.points.create.CreatePointScreen
import com.example.roy.presentation.points.create.CreatePointViewModel
import com.example.roy.presentation.points.detail.PointDetailScreen
import com.example.roy.presentation.points.detail.PointDetailViewModel
import com.example.roy.presentation.navigation.RoyRoute
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.components.overlays.RoySnackbarMessage
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.navigation.MainTab
import com.example.roy.presentation.navigation.AuthReturnCoordinator
import com.example.roy.presentation.navigation.PendingProtectedIntent
import com.example.roy.presentation.navigation.ProtectedNavigation
import com.example.roy.presentation.navigation.TopicChatHandoff
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
    createPoints: CreatePointRepository,
    pointDetails: PointDetailOperations,
    filterStore: PointFilterStore,
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
    val expiration by session.expiration.collectAsStateWithLifecycle()
    val mapModel = viewModel(viewModelStoreOwner = owner) {
        MapViewModel(LoadViewport(points), LoadPoint(points), location, filterStore)
    }
    val goneMessage = stringResource(Res.string.map_point_gone)
    val eventHandler: (RoyRootViewEvent) -> Unit = remember(viewModel) { viewModel::handleEvent }

    fun openPoint(route: RoyRoute.PointDetails) {
        navController.navigate(route) {
            popUpTo<RoyRoute.Map> { inclusive = false }
        }
    }

    fun openChat(pointId: String, topicId: String, chatId: String) {
        val active = session.state.value as? SessionStatus.Active ?: return
        navController.navigate(RoyRoute.TopicChat(pointId, topicId, chatId, isMember = true, scopeId = active.scopeId)) {
            launchSingleTop = true
        }
    }

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
                when (val intent = effect.intent) {
                    is PendingProtectedIntent.CreatePoint -> {
                        continuation.showTarget(effect)
                        navController.navigate(RoyRoute.ProtectedTarget) { launchSingleTop = true }
                    }
                    is PendingProtectedIntent.OpenTopic -> openPoint(RoyRoute.PointDetails(intent.pointId, intent.topicId))
                    is PendingProtectedIntent.AddTopic -> openPoint(RoyRoute.PointDetails(intent.pointId, offerAddTopic = true))
                }
            }
        }
    }

    fun request(intent: PendingProtectedIntent): Boolean {
        val current = navController.currentDestination
        if (current?.hasRoute<RoyRoute.ProtectedTarget>() == true) return false
        val effect = continuation.request(intent, session.state.value) ?: return false
        navigate(effect)
        return true
    }

    fun isCurrentTarget(expected: ProtectedNavigation.Continue): Boolean =
        navController.currentDestination?.hasRoute<RoyRoute.ProtectedTarget>() == true &&
            continuation.currentTarget === expected

    LaunchedEffect(sessionStatus, expiration) {
        val effect = continuation.onSessionChanged(sessionStatus)
        if (sessionStatus == SessionStatus.Guest) {
            navigate(continuation.onSessionExpired(expiration) ?: effect)
        } else {
            navigate(effect)
        }
    }

    LaunchedEffect(backStackEntry) {
        val destination = navController.currentDestination ?: return@LaunchedEffect
        if (!destination.hasRoute<RoyRoute.ProtectedTarget>()) continuation.clearTarget()
    }

    CollectMviActions(viewModel.viewAction) { action ->
        when (action) {
            is RoyRootViewAction.OpenTab -> {
                val current = navController.currentDestination
                if (current?.hasRoute<RoyRoute.ProtectedAuth>() != true &&
                    current?.hasRoute<RoyRoute.ProtectedTarget>() != true &&
                    current?.hasRoute<RoyRoute.MapFilters>() != true &&
                    current?.hasRoute<RoyRoute.PointDetails>() != true
                ) navController.openTab(action.tab)
            }
        }
    }

    RoyShellLayout(
        snackbar = snackbar,
        tabs = {
            val destination = backStackEntry?.destination
            if (destination?.hasRoute<RoyRoute.ProtectedAuth>() != true &&
                destination?.hasRoute<RoyRoute.ProtectedTarget>() != true &&
                destination?.hasRoute<RoyRoute.MapFilters>() != true &&
                destination?.hasRoute<RoyRoute.PointDetails>() != true
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
                    MapScreen(
                        viewModel = mapModel,
                        snackbar = snackbar,
                        nextMessageId = { ++snackbarMessageId },
                        onOpenDetails = { pointId ->
                            if (navController.currentDestination?.hasRoute<RoyRoute.Map>() == true) {
                                navController.navigate(RoyRoute.PointDetails(pointId)) { launchSingleTop = true }
                            }
                        },
                        onOpenFilters = {
                            if (navController.currentDestination?.hasRoute<RoyRoute.Map>() == true) {
                                navController.navigate(RoyRoute.MapFilters) { launchSingleTop = true }
                            }
                        },
                        onCreatePoint = {
                            navController.currentDestination?.hasRoute<RoyRoute.Map>() == true &&
                                request(PendingProtectedIntent.CreatePoint(it))
                        },
                    )
                },
                mapFilters = {
                    val filters = viewModel {
                        MapFilterViewModel(mapModel.viewState.value.appliedFilter, createPoints::getPresets)
                    }
                    MapFilterScreen(
                        viewModel = filters,
                        onBack = {
                            if (navController.currentDestination?.hasRoute<RoyRoute.MapFilters>() == true) {
                                navController.popBackStack()
                            }
                        },
                        onApplied = {
                            if (navController.currentDestination?.hasRoute<RoyRoute.MapFilters>() == true) {
                                mapModel.handleEvent(MapViewEvent.FilterChanged(it))
                                navController.popBackStack()
                            }
                        },
                    )
                },
                pointDetails = { route ->
                    val scopeId = (sessionStatus as? SessionStatus.Active)?.scopeId
                    val detailModel = viewModel(key = "detail/" + route.pointId + "/" + (scopeId ?: "guest")) {
                        PointDetailViewModel(route.pointId, route.topicId, scopeId, route.offerAddTopic, pointDetails)
                    }
                    fun isCurrentDetail(): Boolean {
                        val entry = navController.currentBackStackEntry
                        return entry?.destination?.hasRoute<RoyRoute.PointDetails>() == true &&
                            entry.toRoute<RoyRoute.PointDetails>() == route &&
                            (session.state.value as? SessionStatus.Active)?.scopeId == scopeId
                    }
                    PointDetailScreen(
                        viewModel = detailModel,
                        onBack = { if (isCurrentDetail()) navController.popBackStack() },
                        onRequireJoin = { pointId, topicId, _ ->
                            if (isCurrentDetail()) request(PendingProtectedIntent.OpenTopic(pointId, topicId))
                        },
                        onRequireAddTopic = { if (isCurrentDetail()) request(PendingProtectedIntent.AddTopic(it)) },
                        onOpenChat = { pointId, topicId, chatId ->
                            if (isCurrentDetail()) openChat(pointId, topicId, chatId)
                        },
                        onPointChanged = { pointId, isGone ->
                            mapModel.handleEvent(MapViewEvent.PointChanged(pointId, isGone))
                            if (isGone) {
                                if (isCurrentDetail() && (route.topicId != null || route.offerAddTopic)) {
                                    navController.popBackStack<RoyRoute.Map>(inclusive = false)
                                }
                                snackbar.show(RoySnackbarMessage(++snackbarMessageId, goneMessage))
                            }
                        },
                    )
                },
                topicChat = { route ->
                    fun isCurrentChat(): Boolean {
                        val entry = navController.currentBackStackEntry
                        return entry?.destination?.hasRoute<RoyRoute.TopicChat>() == true &&
                            entry.toRoute<RoyRoute.TopicChat>() == route
                    }
                    if (route.isMember && (sessionStatus as? SessionStatus.Active)?.scopeId == route.scopeId) {
                        TopicChatHandoff { if (isCurrentChat()) navController.popBackStack() }
                    } else {
                        LaunchedEffect(route) {
                            if (isCurrentChat()) navController.popBackStack<RoyRoute.Map>(inclusive = false)
                        }
                    }
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
                    val current = continuation.currentTarget
                    val intent = current?.intent as? PendingProtectedIntent.CreatePoint
                    if (current != null && intent != null && (sessionStatus as? SessionStatus.Active)?.scopeId == current.scopeId) {
                        val createModel = viewModel {
                            CreatePointViewModel(intent.position, CreatePointUseCase(createPoints, session, current.scopeId))
                        }
                        CreatePointScreen(
                            viewModel = createModel,
                            onClose = {
                                if (isCurrentTarget(current)) {
                                    continuation.clearTarget()
                                    navController.popBackStack()
                                }
                            },
                            onCreated = { destination ->
                                if (isCurrentTarget(current) &&
                                    (session.state.value as? SessionStatus.Active)?.scopeId == current.scopeId
                                ) {
                                    continuation.clearTarget()
                                    mapModel.handleEvent(MapViewEvent.PointChanged(destination.pointId, isGone = false))
                                    navController.popBackStack<RoyRoute.Map>(inclusive = false)
                                    openChat(destination.pointId, destination.topicId, destination.chatId)
                                }
                            },
                            onSessionExpired = {
                                if ((session.state.value as? SessionStatus.Active)?.scopeId != current.scopeId &&
                                    isCurrentTarget(current)
                                ) {
                                    continuation.clearTarget()
                                    navController.popBackStack<RoyRoute.Map>(inclusive = false)
                                }
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
