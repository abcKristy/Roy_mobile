package com.example.roy

import androidx.compose.ui.window.ComposeUIViewController
import com.example.roy.data.auth.AuthRemoteDataSource
import com.example.roy.data.filters.FilterPreferencesStore
import com.example.roy.data.chat.chatConnectivity
import platform.Foundation.NSUserDefaults
import com.example.roy.data.auth.IosAuthHttpTransport
import com.example.roy.data.auth.IosSessionStore
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.data.points.PointRemoteDataSource
import com.example.roy.data.points.CreatePointRemoteDataSource
import com.example.roy.data.points.PointDetailRemoteDataSource
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.points.PointDetailOperations
import com.example.roy.location.IosDeviceLocation
import com.example.roy.presentation.navigation.AuthReturnCoordinator
import com.example.roy.data.offline.iosOfflineCache
import com.example.roy.push.IosPush
import androidx.compose.runtime.remember

fun MainViewController(apiOrigin: String?) = ComposeUIViewController {
    val origin = apiOrigin ?: "https://roy.invalid"
    val transport = remember { IosAuthHttpTransport() }
    val points = remember(origin) { PointRemoteDataSource(origin, transport) }
    val location = remember { IosDeviceLocation() }
    val cache = remember { iosOfflineCache() }
    val session = remember(origin) {
        SessionCoordinator(
            AuthRemoteDataSource(origin, transport),
            IosSessionStore(),
            onPrivateSessionEnded = { cache.clearPrivate() },
            allowOfflineRestore = true,
        )
    }
    val continuation = remember(session) { AuthReturnCoordinator() }
    val createPoints = remember(origin) { CreatePointRemoteDataSource(origin, transport) }
    val pointDetails = remember(session, points, createPoints) {
        PointDetailOperations(LoadPoint(points), session, PointDetailRemoteDataSource(origin, transport), createPoints::getPresets)
    }
    val filterStore = remember {
        val preferences = NSUserDefaults.standardUserDefaults
        FilterPreferencesStore(
            readValue = { preferences.stringForKey("point_filter") },
            writeValue = { preferences.setObject(it, forKey = "point_filter") },
        )
    }
    val connectivity = remember { chatConnectivity() }
    App(
        session, points, location, continuation, createPoints, pointDetails, filterStore, origin, transport, connectivity,
        notificationTargets = IosPush.notificationTargets, pushRuntime = IosPush.runtime, offlineCache = cache,
    )
}
