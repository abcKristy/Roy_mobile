package com.example.roy.presentation.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.presentation.components.DestructiveButton
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_logout
import roy.shared.generated.resources.auth_logout_failed
import roy.shared.generated.resources.auth_network
import roy.shared.generated.resources.tab_profile

@Composable
internal fun ProfileSessionView(
    status: RoyRootOperations,
    eventHandler: (RoyRootViewEvent) -> Unit,
) {
    val colors = RoyTheme.colors
    val spacing = RoyTheme.dimensions.spacing
    Column(
        Modifier.fillMaxWidth().padding(spacing.comfortable),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        BasicText(
            stringResource(Res.string.tab_profile),
            Modifier.semantics { heading() },
            style = RoyTheme.typography.headingMedium.copy(color = colors.textPrimary),
        )
        DestructiveButton(
            label = stringResource(Res.string.auth_logout),
            isEnabled = !status.isLoggingOut,
            isSubmitting = status.isLoggingOut,
            onClick = { eventHandler(RoyRootViewEvent.OnLogout) },
        )
        status.logoutError?.let { error ->
            BasicText(
                stringResource(if (error == AuthErrorCode.Network) Res.string.auth_network else Res.string.auth_logout_failed),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = RoyTheme.typography.bodySmall.copy(color = colors.error),
            )
        }
    }
}
