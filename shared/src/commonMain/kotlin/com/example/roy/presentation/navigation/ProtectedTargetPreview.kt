package com.example.roy.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.roy.domain.map.Position
import com.example.roy.domain.navigation.DraftContext
import com.example.roy.domain.navigation.ProtectedTargetResult
import com.example.roy.presentation.theme.RoyTheme

@Preview
@Composable
private fun DraftHandoffPreview() {
    RoyTheme(darkTheme = false) {
        ProtectedTargetView(
            ProtectedTargetResult.Ready(
                PreparedContinuation.Draft(Position(55.75, 37.61), DraftContext(1, 3, listOf("Тверская улица"))),
            ),
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun ForbiddenTargetPreview() {
    RoyTheme(darkTheme = true) {
        ProtectedTargetView(ProtectedTargetResult.Forbidden, onBack = {}, onRetry = {})
    }
}
