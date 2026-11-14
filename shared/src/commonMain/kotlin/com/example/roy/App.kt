package com.example.roy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.location.DeviceLocation
import com.example.roy.domain.map.PointRepository
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.shell.RoyRootScreen
import com.example.roy.presentation.theme.RoyTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_network
import roy.shared.generated.resources.auth_restore
import roy.shared.generated.resources.auth_storage_failed

@Composable
internal fun App(session: SessionCoordinator, points: PointRepository, location: DeviceLocation) {
    val state by session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(session) { session.restore() }
    RoyTheme {
        when (val current = state) {
            SessionStatus.Restoring -> LoadingState(LoadingMode.Fullscreen, stringResource(Res.string.auth_restore))
            SessionStatus.Guest -> key("guest") { RoyRootScreen(session, points, location, isGuest = true) }
            SessionStatus.StorageUnavailable, SessionStatus.VerificationUnavailable -> key("guest") {
                val message = if (current == SessionStatus.StorageUnavailable) {
                    Res.string.auth_storage_failed
                } else {
                    Res.string.auth_network
                }
                RoyRootScreen(
                    session, points, location, isGuest = true,
                    guestProfile = {
                        ErrorState(
                            safeMessage = stringResource(message),
                            isRetryEnabled = true,
                            onRetry = { scope.launch { session.restore() } },
                        )
                    },
                )
            }
            is SessionStatus.Active -> key(current.scopeId) { RoyRootScreen(session, points, location, isGuest = false) }
        }
    }
}
