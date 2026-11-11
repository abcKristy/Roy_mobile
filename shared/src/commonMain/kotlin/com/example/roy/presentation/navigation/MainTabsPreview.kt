package com.example.roy.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.presentation.theme.RoyTheme

@Preview(name = "Tabs light")
@Composable
fun RoyTabsLightPreview() {
    RoyTheme(darkTheme = false) { TabsPreviewContent() }
}

@Preview(name = "Tabs dark")
@Composable
fun RoyTabsDarkPreview() {
    RoyTheme(darkTheme = true) { TabsPreviewContent() }
}

@Composable
private fun TabsPreviewContent() {
    var selected by remember { mutableStateOf(MainTab.Map) }
    var selections by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(RoyTheme.colors.background).safeContentPadding()) {
        Box(Modifier.weight(1f).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            BasicText("Переходов: $selections", style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textPrimary))
        }
        MainTabs(
            selectedTab = selected,
            onTabSelected = { tab ->
                selected = tab
                selections++
            },
        )
    }
}
