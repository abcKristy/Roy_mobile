package com.example.roy.presentation.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.theme.RoyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt

data class RoySnackbarAction(val label: String, val onClick: () -> Unit)

data class RoySnackbarMessage(
    val id: Long,
    val text: String,
    val action: RoySnackbarAction? = null,
    val durationMillis: Long = 4_000,
) {
    init {
        require(durationMillis > 0)
    }
}

class RoySnackbarHostState {
    private data class Slot(val lastId: Long? = null, val message: RoySnackbarMessage? = null)

    private val slot = MutableStateFlow(Slot())
    internal val messages = slot.map { it.message }.distinctUntilChanged()

    /** All producers of this host share a strictly increasing sequence of message IDs. */
    fun show(message: RoySnackbarMessage) {
        slot.update { current ->
            if (current.lastId?.let { message.id <= it } == true) current else Slot(message.id, message)
        }
    }

    fun dismiss(id: Long) {
        slot.update { current ->
            if (current.message?.id == id) current.copy(message = null) else current
        }
    }

    internal fun performAction(id: Long) {
        while (true) {
            val current = slot.value
            val message = current.message ?: return
            if (message.id != id) return
            val action = message.action ?: return
            if (slot.compareAndSet(current, current.copy(message = null))) {
                action.onClick()
                return
            }
        }
    }
}

@Composable
fun RoySnackbarHost(state: RoySnackbarHostState, modifier: Modifier = Modifier) {
    val current by state.messages.collectAsState(initial = null)
    val message = current ?: return
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(state, message.id) {
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = message.durationMillis,
            containsText = true,
            containsControls = message.action != null,
        ) ?: message.durationMillis
        delay(timeout)
        state.dismiss(message.id)
    }

    val colors = RoyTheme.colors
    val dimensions = RoyTheme.dimensions
    val spacing = dimensions.spacing
    val shape = RoundedCornerShape(dimensions.radii.large)
    var drag by remember(state, message.id) { mutableFloatStateOf(0f) }
    Row(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .heightIn(min = spacing.space64)
            .offset { IntOffset(drag.roundToInt(), 0) }
            .shadow(spacing.small, shape)
            .background(colors.glass, shape)
            .border(1.dp, colors.outline, shape)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                dismiss {
                    state.dismiss(message.id)
                    true
                }
            }
            .pointerInput(state, message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag) >= size.width / 4f) state.dismiss(message.id)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { change, distance ->
                    change.consume()
                    drag += distance
                }
            }
            .padding(horizontal = spacing.medium, vertical = spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(spacing.extraSmall, spacing.extraLarge).background(colors.accent, shape))
        BasicText(
            text = message.text,
            modifier = Modifier.weight(2f),
            style = RoyTheme.typography.bodySmall.copy(color = colors.textPrimary),
        )
        message.action?.let { action ->
            TertiaryButton(
                label = action.label,
                onClick = { state.performAction(message.id) },
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
