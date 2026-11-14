package com.example.roy

import androidx.compose.ui.window.ComposeUIViewController
import com.example.roy.data.auth.AuthRemoteDataSource
import com.example.roy.data.auth.IosAuthHttpTransport
import com.example.roy.data.auth.IosSessionStore
import com.example.roy.domain.auth.SessionCoordinator
import androidx.compose.runtime.remember

fun MainViewController(apiOrigin: String?) = ComposeUIViewController {
    val session = remember(apiOrigin) {
        SessionCoordinator(
            AuthRemoteDataSource(apiOrigin ?: "https://roy.invalid", IosAuthHttpTransport()),
            IosSessionStore(),
        )
    }
    App(session)
}
