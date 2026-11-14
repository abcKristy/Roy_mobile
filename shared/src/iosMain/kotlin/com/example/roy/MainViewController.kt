package com.example.roy

import androidx.compose.ui.window.ComposeUIViewController
import com.example.roy.data.auth.AuthRemoteDataSource
import com.example.roy.data.auth.IosAuthHttpTransport
import com.example.roy.data.auth.IosSessionStore
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.data.points.PointRemoteDataSource
import com.example.roy.location.IosDeviceLocation
import androidx.compose.runtime.remember

fun MainViewController(apiOrigin: String?) = ComposeUIViewController {
    val origin = apiOrigin ?: "https://roy.invalid"
    val transport = remember { IosAuthHttpTransport() }
    val points = remember(origin) { PointRemoteDataSource(origin, transport) }
    val location = remember { IosDeviceLocation() }
    val session = remember(origin) {
        SessionCoordinator(
            AuthRemoteDataSource(origin, transport),
            IosSessionStore(),
        )
    }
    App(session, points, location)
}
