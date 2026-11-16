package com.example.roy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.chat.ChatHistoryRemoteDataSource
import com.example.roy.data.chat.ChatListRemoteDataSource
import com.example.roy.data.chat.ChatRealtimeClient
import com.example.roy.data.chat.ChatWriteRemoteDataSource
import com.example.roy.data.chat.createChatSocketTransport
import com.example.roy.data.meetings.MeetingRemoteDataSource
import com.example.roy.data.meetings.MeetingAvatarAdapter
import com.example.roy.data.notifications.NotificationRemoteDataSource
import com.example.roy.data.notifications.PushRegistration
import com.example.roy.data.offline.OfflineCache
import com.example.roy.data.profile.ProfileRemoteDataSource
import com.example.roy.domain.notifications.NotificationTarget
import com.example.roy.push.PushRuntime
import com.example.roy.domain.chat.ChatListOperations
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.location.DeviceLocation
import com.example.roy.domain.map.PointRepository
import com.example.roy.domain.map.PointFilterStore
import com.example.roy.domain.points.CreatePointRepository
import com.example.roy.domain.points.PointDetailOperations
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.shell.RoyRootScreen
import com.example.roy.presentation.navigation.AuthReturnCoordinator
import com.example.roy.presentation.theme.RoyTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_network
import roy.shared.generated.resources.auth_restore
import roy.shared.generated.resources.auth_storage_failed

@Composable
internal fun App(
    session: SessionCoordinator,
    points: PointRepository,
    location: DeviceLocation,
    continuation: AuthReturnCoordinator,
    createPoints: CreatePointRepository,
    pointDetails: PointDetailOperations,
    filterStore: PointFilterStore,
    origin: String,
    transport: AuthHttpTransport,
    connectivity: Flow<Boolean>,
    meetingTargets: Flow<MeetingNotificationTarget> = emptyFlow(),
    notificationTargets: Flow<NotificationTarget> = emptyFlow(),
    pushRuntime: PushRuntime? = null,
    offlineCache: OfflineCache? = null,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val readyConnectivity = remember(session, connectivity) { MutableStateFlow(false) }
    val scope = rememberCoroutineScope()
    val chatHistory = remember(origin, transport) { ChatHistoryRemoteDataSource(origin, transport) }
    val chatWriter = remember(origin, transport) { ChatWriteRemoteDataSource(origin, transport) }
    val meetings = remember(origin, transport) { MeetingRemoteDataSource(origin, transport) }
    val meetingAvatars = remember(origin, transport, session) { MeetingAvatarAdapter(origin, transport, session) }
    val profiles = remember(origin, transport) { ProfileRemoteDataSource(origin, transport) }
    val notifications = remember(origin, transport) { NotificationRemoteDataSource(origin, transport) }
    val registration = remember(origin, transport, session, pushRuntime) {
        pushRuntime?.let { PushRegistration(origin, transport, session, it) }
    }
    val chatList = remember(origin, transport, session) {
        ChatListOperations(session, ChatListRemoteDataSource(origin, transport))
    }
    val chatRealtime = remember(origin, session, scope) {
        ChatRealtimeClient(origin, createChatSocketTransport(), session, scope)
    }
    LaunchedEffect(session) { session.restore() }
    LaunchedEffect(session, connectivity) {
        connectivity.collectLatest { online ->
            session.setOnline(online)
            readyConnectivity.value = online
        }
    }
    LaunchedEffect(session, state) {
        if (state is SessionStatus.Active && readyConnectivity.value) session.setOnline(true)
    }
    LifecycleResumeEffect(session) {
        val verification = scope.launch { if (readyConnectivity.value) session.setOnline(true) }
        onPauseOrDispose { verification.cancel() }
    }
    LaunchedEffect(registration, readyConnectivity) { registration?.observe(readyConnectivity) }
    RoyTheme {
        val current = state
        if (current == SessionStatus.Restoring) {
            LoadingState(LoadingMode.Fullscreen, stringResource(Res.string.auth_restore))
        } else {
            val profileError = when (current) {
                SessionStatus.StorageUnavailable -> Res.string.auth_storage_failed
                SessionStatus.VerificationUnavailable -> Res.string.auth_network
                else -> null
            }
            val guestProfile: (@Composable () -> Unit)? = if (profileError != null) {
                {
                    ErrorState(
                        safeMessage = stringResource(profileError),
                        isRetryEnabled = true,
                        onRetry = { scope.launch { session.restore() } },
                    )
                }
            } else null
            RoyRootScreen(
                session, points, location, current, continuation, createPoints, pointDetails, filterStore,
                chatHistory, chatWriter, chatList, chatRealtime, meetings, meetingAvatars::forScope,
                readyConnectivity, meetingTargets, notificationTargets, profiles, notifications,
                pushRuntime, registration, offlineCache, guestProfile,
            )
        }
    }
}
