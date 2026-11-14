package com.example.roy.presentation.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.painterResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_four
import roy.shared.generated.resources.map_four_selected
import roy.shared.generated.resources.map_overflow
import roy.shared.generated.resources.map_three
import roy.shared.generated.resources.map_three_selected
import roy.shared.generated.resources.map_two
import roy.shared.generated.resources.map_two_selected

@Composable
internal fun MapMultiTopicPin(
    topicCount: Int,
    isSelected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = when (topicCount) {
        2 -> if (isSelected) Res.drawable.map_two_selected else Res.drawable.map_two
        3 -> if (isSelected) Res.drawable.map_three_selected else Res.drawable.map_three
        else -> if (isSelected) Res.drawable.map_four_selected else Res.drawable.map_four
    }
    MapPinBody(image, isSelected, contentDescription, onClick, modifier) {
        if (topicCount > 4) {
            Image(
                painter = painterResource(Res.drawable.map_overflow),
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .offset(y = if (isSelected) (-24).dp else (-19).dp)
                    .size(24.dp)
                    .background(RoyTheme.colors.surfaceElevated, CircleShape),
                colorFilter = ColorFilter.tint(RoyTheme.colors.textPrimary),
            )
        }
    }
}
