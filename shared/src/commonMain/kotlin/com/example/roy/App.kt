package com.example.roy

import androidx.compose.runtime.Composable
import com.example.roy.presentation.shell.RoyRootScreen
import com.example.roy.presentation.theme.RoyTheme

@Composable
fun App() {
    RoyTheme { RoyRootScreen() }
}
