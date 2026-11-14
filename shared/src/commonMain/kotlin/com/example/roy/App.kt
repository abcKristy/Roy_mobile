package com.example.roy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.presentation.auth.AuthNavHost
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
internal fun App(session: SessionCoordinator) {
    val state by session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(session) { session.restore() }
    RoyTheme {
        when (val current = state) {
            SessionStatus.Restoring -> LoadingState(LoadingMode.Fullscreen, stringResource(Res.string.auth_restore))
            SessionStatus.Guest -> AuthNavHost(session)
            SessionStatus.StorageUnavailable -> ErrorState(
                safeMessage = stringResource(Res.string.auth_storage_failed),
                isRetryEnabled = true,
                onRetry = { scope.launch { session.restore() } },
            )
            SessionStatus.VerificationUnavailable -> ErrorState(
                safeMessage = stringResource(Res.string.auth_network),
                isRetryEnabled = true,
                onRetry = { scope.launch { session.restore() } },
            )
            is SessionStatus.Active -> key(current.scopeId) { RoyRootScreen(session) }
        }
    }
}
