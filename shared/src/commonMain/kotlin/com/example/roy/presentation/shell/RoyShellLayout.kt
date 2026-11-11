package com.example.roy.presentation.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.roy.presentation.components.overlays.RoySnackbarHost
import com.example.roy.presentation.components.overlays.RoySnackbarHostState
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.app_name

@Composable
internal fun RoyShellLayout(
    snackbar: RoySnackbarHostState,
    tabs: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = RoyTheme.colors
    val spacing = RoyTheme.dimensions.spacing
    val insets = WindowInsets.safeContent
    val horizontalInsets = insets.only(WindowInsetsSides.Horizontal)
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(insets.only(WindowInsetsSides.Top))) {
        BasicText(
            text = stringResource(Res.string.app_name),
            modifier = Modifier.windowInsetsPadding(horizontalInsets).padding(spacing.medium).semantics { heading() },
            style = RoyTheme.typography.headingLarge.copy(color = colors.textPrimary),
        )
        Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(horizontalInsets)) {
            content()
            RoySnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(spacing.medium))
        }
        tabs()
    }
}
