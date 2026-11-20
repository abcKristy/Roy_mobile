package com.example.roy.presentation.shell

import androidx.compose.runtime.Composable
import com.example.roy.MeetingNotificationTarget
import com.example.roy.domain.auth.AuthResult
import roy.shared.generated.resources.notifications_target_unavailable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.navigation.MainTab
import com.example.roy.presentation.navigation.AuthReturnCoordinator
import com.example.roy.presentation.navigation.PendingProtectedIntent
import com.example.roy.presentation.navigation.ProtectedNavigation
import com.example.roy.data.chat.ChatRealtimeClient
import com.example.roy.domain.meetings.MeetingRepository
import com.example.roy.domain.meetings.MeetingVoterAvatarLoader
import com.example.roy.presentation.meetings.MeetingViewModel
import com.example.roy.domain.chat.ChatHistoryRepository
import com.example.roy.domain.chat.ChatRealtimeEvent
import com.example.roy.data.offline.OfflineCache
import com.example.roy.data.notifications.PushRegistration
import com.example.roy.domain.profile.ProfileRepository
import com.example.roy.domain.notifications.NotificationRepository
import com.example.roy.domain.notifications.NotificationTarget
import com.example.roy.domain.notifications.PushPermission
import com.example.roy.presentation.notifications.NotificationMode
import com.example.roy.presentation.notifications.NotificationScreen
import com.example.roy.presentation.notifications.NotificationViewModel
import com.example.roy.presentation.profile.ProfileScreen
import com.example.roy.presentation.profile.ProfileViewModel
import com.example.roy.push.PushRuntime
import com.example.roy.domain.chat.ChatReadReply
import com.example.roy.domain.chat.ChatListOperations
import com.example.roy.domain.chat.ChatReadCursor
import com.example.roy.domain.chat.ChatWriteRepository
import com.example.roy.presentation.chat.ChatReportDialog
import com.example.roy.presentation.chat.ChatReportViewModel
import com.example.roy.presentation.chat.LeaveTopicResult
import com.example.roy.presentation.chat.TopicChatScreen
import com.example.roy.presentation.chat.TopicChatViewModel
import com.example.roy.presentation.chats.ChatListScreen
import com.example.roy.presentation.chats.ChatListViewModel
import com.example.roy.presentation.chats.ChatListViewEvent
import com.example.roy.presentation.chats.EmptyChatList
import com.example.roy.presentation.points.detail.PointDetailEvent
import com.example.roy.presentation.navigation.RoyNavHost
import com.example.roy.presentation.navigation.mainTab
import com.example.roy.presentation.navigation.openTab
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_restore
import roy.shared.generated.resources.protected_unavailable
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
    chatHistory: ChatHistoryRepository,
    chatWriter: ChatWriteRepository,
    chatList: ChatListOperations,
    chatRealtime: ChatRealtimeClient,
    meetings: MeetingRepository,
    meetingAvatarsForScope: (Long) -> MeetingVoterAvatarLoader,
    connectivity: Flow<Boolean>,
    meetingTargets: Flow<MeetingNotificationTarget>,
    notificationTargets: Flow<NotificationTarget>,
    profiles: ProfileRepository,
    notifications: NotificationRepository,
    pushRuntime: PushRuntime?,
    registration: PushRegistration?,
    offlineCache: OfflineCache?,
    guestProfile: (@Composable () -> Unit)? = null,
) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val activeChatScope = (sessionStatus as? SessionStatus.Active)?.scopeId
    val chatsOwner = remember(activeChatScope) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(chatsOwner) { onDispose { chatsOwner.viewModelStore.clear() } }
    val listModel = activeChatScope?.let { scopeId ->
        viewModel(viewModelStoreOwner = chatsOwner) { ChatListViewModel(scopeId, chatList, offlineCache, session, connectivity) }
    }
    val notificationModel = activeChatScope?.let { scopeId ->
        viewModel(viewModelStoreOwner = chatsOwner) {
            NotificationViewModel(scopeId, session, notifications, connectivity)
        }
    }
    val profileModel = activeChatScope?.let { scopeId ->
        viewModel(viewModelStoreOwner = chatsOwner) {
            ProfileViewModel(scopeId, session, profiles, connectivity) { registration?.logout(scopeId) ?: session.logout(scopeId) }
        }
    }
    val latestNotificationModel by rememberUpdatedState(notificationModel)
    val permission = pushRuntime?.authorization?.collectAsStateWithLifecycle()?.value
        ?.let { PushPermission.valueOf(it.name) } ?: PushPermission.Denied
    val viewModel = viewModel(viewModelStoreOwner = owner) { RoyRootViewModel(session) }
    val operations by viewModel.viewState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbar = remember { RoySnackbarHostState() }
    var snackbarMessageId by remember { mutableLongStateOf(0L) }
    var changedPoint by remember { mutableStateOf<LeaveTopicResult?>(null) }
    val expiration by session.expiration.collectAsStateWithLifecycle()
    val mapModel = viewModel(viewModelStoreOwner = owner) {
        MapViewModel(LoadViewport(points), LoadPoint(points), location, filterStore, offlineCache, connectivity) {
            latestNotificationModel?.onViewportSettled(it)
        }
    }
    val goneMessage = stringResource(Res.string.map_point_gone)
    val notificationUnavailable = stringResource(Res.string.notifications_target_unavailable)
    val eventHandler: (RoyRootViewEvent) -> Unit = remember(viewModel) { viewModel::handleEvent }

    fun openPoint(route: RoyRoute.PointDetails) {
        navController.navigate(route) {
            popUpTo<RoyRoute.Map> { inclusive = false }
        }
    }

    fun openChat(pointId: String, topicId: String, chatId: String, proposalId: String? = null) {
        val active = session.state.value as? SessionStatus.Active ?: return
        navController.navigate(RoyRoute.TopicChat(pointId, topicId, chatId, isMember = true, scopeId = active.scopeId, proposalId = proposalId)) {
            launchSingleTop = proposalId == null
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
                    is PendingProtectedIntent.CreatePoint, is PendingProtectedIntent.OpenChatTarget -> {
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

    var deferredMeeting by remember { mutableStateOf<Pair<Long?, MeetingNotificationTarget>?>(null) }
    var deferredNotification by remember { mutableStateOf<Pair<Long?, NotificationTarget>?>(null) }
    LaunchedEffect(meetingTargets) {
        meetingTargets.collect { target ->
            deferredMeeting = (session.state.value as? SessionStatus.Active)?.scopeId to target
        }
    }
    LaunchedEffect(notificationTargets) {
        notificationTargets.collect { target ->
            deferredNotification = (session.state.value as? SessionStatus.Active)?.scopeId to target
        }
    }
    fun openNotification(target: NotificationTarget) {
        if (target.chatId == null) {
            openPoint(RoyRoute.PointDetails(target.pointId))
            return
        }
        navigate(continuation.request(
            PendingProtectedIntent.OpenChatTarget(target.pointId, target.chatId, target.proposalId), session.state.value,
        ))
    }
    LaunchedEffect(deferredNotification, backStackEntry, activeChatScope) {
        val (scope, target) = deferredNotification ?: return@LaunchedEffect
        if (activeChatScope == null || (scope != null && scope != activeChatScope)) {
            deferredNotification = null
            return@LaunchedEffect
        }
        val destination = navController.currentDestination ?: return@LaunchedEffect
        if (destination.hasRoute<RoyRoute.ProtectedTarget>() || destination.hasRoute<RoyRoute.ProtectedAuth>()) return@LaunchedEffect
        val result = notificationModel?.confirmNotification(target.notificationId)
        if (session.state.value != SessionStatus.Active(activeChatScope)) return@LaunchedEffect
        deferredNotification = null
        val confirmed = (result as? AuthResult.Success)?.value?.target
        if (confirmed == null) {
            snackbar.show(RoySnackbarMessage(++snackbarMessageId, notificationUnavailable))
        } else {
            openNotification(confirmed)
        }
    }
    LaunchedEffect(deferredMeeting, backStackEntry, activeChatScope) {
        val (scope, target) = deferredMeeting ?: return@LaunchedEffect
        if (scope != null && scope != activeChatScope) {
            deferredMeeting = null
            return@LaunchedEffect
        }
        val destination = navController.currentDestination ?: return@LaunchedEffect
        if (destination.hasRoute<RoyRoute.ProtectedTarget>() || destination.hasRoute<RoyRoute.ProtectedAuth>()) {
            return@LaunchedEffect
        }
        deferredMeeting = null
        navigate(continuation.request(
            PendingProtectedIntent.OpenChatTarget(target.pointId, target.chatId, target.proposalId), session.state.value,
        ))
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
                    current?.hasRoute<RoyRoute.PointDetails>() != true &&
                    current?.hasRoute<RoyRoute.Notifications>() != true &&
                    current?.hasRoute<RoyRoute.TopicChat>() != true
                ) navController.openTab(action.tab)
            }
        }
    }

    RoyShellLayout(
        snackbar = snackbar,
        showTitle = backStackEntry?.destination?.hasRoute<RoyRoute.TopicChat>() != true,
        tabs = {
            val destination = backStackEntry?.destination
            if (destination?.hasRoute<RoyRoute.ProtectedAuth>() != true &&
                destination?.hasRoute<RoyRoute.ProtectedTarget>() != true &&
                destination?.hasRoute<RoyRoute.MapFilters>() != true &&
                destination?.hasRoute<RoyRoute.PointDetails>() != true &&
                destination?.hasRoute<RoyRoute.Notifications>() != true &&
                destination?.hasRoute<RoyRoute.TopicChat>() != true
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
                        profileModel != null -> ProfileScreen(
                            profileModel,
                            onOpenNotifications = { navController.navigate(RoyRoute.Notifications()) },
                            onOpenNotificationSettings = { navController.navigate(RoyRoute.Notifications(settings = true)) },
                        )
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
                    LaunchedEffect(backStackEntry, changedPoint, detailModel) {
                        if (isCurrentDetail() && changedPoint?.pointId == route.pointId) {
                            changedPoint = null
                            detailModel.handleEvent(PointDetailEvent.OnRetry)
                        }
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
                        val meetingModel = viewModel(key = "meetings/" + route.scopeId + "/" + route.chatId + "/" + route.proposalId) {
                            MeetingViewModel(route.scopeId, route.chatId, session, chatHistory, meetings, route.proposalId)
                        }
                        val chatModel = viewModel(key = "chat/" + route.scopeId + "/" + route.chatId) {
                            TopicChatViewModel(
                                route.scopeId, route.pointId, route.topicId, route.chatId, session, chatHistory,
                                pointDetails, ChatReadCursor(route.scopeId, route.chatId, chatList), chatWriter,
                                { chatRealtime.events(route.scopeId, route.chatId) }, connectivity,
                                { listModel?.handleEvent(ChatListViewEvent.Refresh) },
                                meetingModel::onRealtimeChange,
                                offlineCache,
                            )
                        }
                        fun onMembershipChanged(result: LeaveTopicResult) {
                            if (!isCurrentChat() || session.state.value != SessionStatus.Active(route.scopeId)) return
                            changedPoint = result
                            listModel?.handleEvent(ChatListViewEvent.MembershipLost(result.chatId))
                            mapModel.handleEvent(MapViewEvent.PointChanged(result.pointId, result.isPointGone))
                        }
                        TopicChatScreen(
                            viewModel = chatModel,
                            onBack = {
                                if (isCurrentChat()) {
                                    if (changedPoint?.isPointGone == true && changedPoint?.chatId == route.chatId) {
                                        navController.popBackStack<RoyRoute.Map>(inclusive = false)
                                    } else navController.popBackStack()
                                }
                            },
                            onLeft = { result ->
                                onMembershipChanged(result)
                                if (isCurrentChat() && session.state.value == SessionStatus.Active(route.scopeId)) {
                                    if (result.isPointGone) navController.popBackStack<RoyRoute.Map>(inclusive = false)
                                    else navController.popBackStack()
                                }
                            },
                            onAccessLost = ::onMembershipChanged,
                            meetings = meetingModel,
                            loadVoterAvatar = remember(route.scopeId, meetingAvatarsForScope) {
                                meetingAvatarsForScope(route.scopeId)
                            },
                            reportContent = { target, onDismiss ->
                                val report = viewModel(key = "report/" + route.scopeId + "/" + route.chatId + "/" + target.userId) {
                                    ChatReportViewModel(target, route.chatId, route.scopeId, session, chatWriter, connectivity)
                                }
                                ChatReportDialog(target, report, onDismiss)
                            },
                        )
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
                    val meeting = current?.intent as? PendingProtectedIntent.OpenChatTarget
                    if (current != null && meeting != null && sessionStatus == SessionStatus.Active(current.scopeId)) {
                        var retry by remember(current) { mutableLongStateOf(0L) }
                        var failed by remember(current) { mutableStateOf(false) }
                        if (failed) {
                            ErrorState(
                                stringResource(Res.string.protected_unavailable),
                                isRetryEnabled = true,
                                onRetry = { failed = false; retry++ },
                            )
                        } else LoadingState(LoadingMode.Fullscreen, stringResource(Res.string.auth_restore))
                        LaunchedEffect(current, retry) {
                            val result = session.readProtected(current.scopeId) { chatHistory.detail(meeting.chatId, it) }
                            if (!isCurrentTarget(current) || session.state.value != SessionStatus.Active(current.scopeId)) {
                                return@LaunchedEffect
                            }
                            if (result is AuthResult.Failure) {
                                failed = true
                                return@LaunchedEffect
                            }
                            val detail = ((result as? AuthResult.Success)?.value as? ChatReadReply.Data)?.value
                            continuation.clearTarget()
                            navController.popBackStack()
                            if (detail != null && detail.id == meeting.chatId && detail.pointId == meeting.pointId) {
                                openChat(detail.pointId, detail.topicId, detail.id, meeting.proposalId)
                            } else {
                                snackbar.show(RoySnackbarMessage(++snackbarMessageId, goneMessage))
                            }
                        }
                    } else if (current != null && intent != null && (sessionStatus as? SessionStatus.Active)?.scopeId == current.scopeId) {
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
                chats = {
                    val active = sessionStatus as? SessionStatus.Active
                    val openMap = {
                        if (navController.currentDestination?.hasRoute<RoyRoute.Chats>() == true) {
                            eventHandler(RoyRootViewEvent.OnTabSelected(MainTab.Map))
                        }
                    }
                    if (active == null || listModel == null) {
                        EmptyChatList(openMap)
                    } else {
                        ChatListScreen(
                            viewModel = listModel,
                            realtime = remember(chatRealtime, active.scopeId) {
                                { ids -> chatRealtime.events(active.scopeId, ids) }
                            },
                            onOpenChat = { row ->
                                if (session.state.value == active &&
                                    navController.currentDestination?.hasRoute<RoyRoute.Chats>() == true
                                ) openChat(row.pointId, row.topicId, row.chatId)
                            },
                            onOpenMap = openMap,
                        )
                    }
                },
                notifications = { route ->
                    if (notificationModel == null) {
                        LaunchedEffect(sessionStatus) { navController.popBackStack() }
                    } else {
                        val lifecycle = LocalLifecycleOwner.current.lifecycle
                        LaunchedEffect(lifecycle, activeChatScope, route.settings) {
                            val scopeId = activeChatScope ?: return@LaunchedEffect
                            if (!route.settings) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                chatRealtime.events(scopeId).collect { event ->
                                    if (event == ChatRealtimeEvent.NotificationsChanged || event == ChatRealtimeEvent.Resync) {
                                        notificationModel.invalidateCenter()
                                    }
                                }
                            }
                        }
                        NotificationScreen(
                            notificationModel,
                            if (route.settings) NotificationMode.Settings else NotificationMode.Center,
                            permission,
                            onRequestPermission = { pushRuntime?.requestPermission() },
                            onBack = { navController.popBackStack() },
                            onOpenTarget = ::openNotification,
                        )
                    }
                },
            )
        },
    )
}
