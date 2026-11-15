package com.example.roy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
) {
    val state by session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(session) { session.restore() }
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
            RoyRootScreen(session, points, location, current, continuation, createPoints, pointDetails, filterStore, guestProfile)
        }
    }
}
