package com.example.roy

import androidx.compose.ui.window.ComposeUIViewController
import com.example.roy.data.auth.AuthRemoteDataSource
import com.example.roy.data.auth.IosAuthHttpTransport
import com.example.roy.data.auth.IosSessionStore
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.data.points.PointRemoteDataSource
import com.example.roy.data.points.ProtectedTargetRemoteDataSource
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.navigation.ResolveProtectedTarget
import com.example.roy.location.IosDeviceLocation
import com.example.roy.presentation.navigation.AuthReturnCoordinator
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
    val continuation = remember(session) { AuthReturnCoordinator() }
    val resolver = remember(session, points) {
        ResolveProtectedTarget(LoadPoint(points), session, ProtectedTargetRemoteDataSource(origin, transport))
    }
    App(session, points, location, continuation, resolver)
}
