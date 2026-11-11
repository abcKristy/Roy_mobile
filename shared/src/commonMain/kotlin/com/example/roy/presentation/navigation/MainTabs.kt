package com.example.roy.presentation.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.tab_chats
import roy.shared.generated.resources.tab_map
import roy.shared.generated.resources.tab_profile

internal enum class MainTab(val title: StringResource, val icon: DrawableResource) {
    Profile(Res.string.tab_profile, Res.drawable.tab_profile),
    Map(Res.string.tab_map, Res.drawable.tab_map),
    Chats(Res.string.tab_chats, Res.drawable.tab_chats),
}

@Composable
internal fun MainTabs(selectedTab: MainTab, onTabSelected: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = RoyTheme.colors
    val dimensions = RoyTheme.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated)
            .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .heightIn(min = 80.dp)
            .selectableGroup()
            .padding(horizontal = dimensions.spacing.small, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in MainTab.entries) {
            MainTabItem(
                tab = tab,
                isSelected = tab == selectedTab,
                onClick = { if (tab != selectedTab) onTabSelected(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MainTabItem(tab: MainTab, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = RoyTheme.colors
    val dimensions = RoyTheme.dimensions
    val title = stringResource(tab.title)
    val interactions = remember { MutableInteractionSource() }
    val isFocused by interactions.collectIsFocusedAsState()
    val focus = if (isFocused) {
        Modifier.border(2.dp, colors.textPrimary, RoundedCornerShape(dimensions.radii.medium))
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .heightIn(min = dimensions.minimumTouchTarget)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                interactionSource = interactions,
                indication = null,
                onClick = onClick,
            )
            .clearAndSetSemantics { contentDescription = title }
            .then(focus)
            .padding(horizontal = dimensions.spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensions.spacing.extraSmall, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(tab.icon),
            contentDescription = null,
            modifier = Modifier.size(dimensions.iconSize),
            colorFilter = ColorFilter.tint(if (isSelected) colors.pin else colors.textSecondary),
        )
        BasicText(
            text = title,
            style = RoyTheme.typography.caption.copy(
                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                textAlign = TextAlign.Center,
            ),
        )
        val underline = Modifier.size(width = dimensions.spacing.medium, height = 3.dp)
        Box(if (isSelected) underline.background(colors.pin, RoundedCornerShape(dimensions.radii.pill)) else underline)
    }
}
