package com.example.roy.shared

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roy.App
import com.example.roy.data.auth.AndroidAuthHttpTransport
import com.example.roy.data.auth.AndroidSessionStore
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.data.auth.AuthRemoteDataSource
import com.example.roy.data.filters.FilterPreferencesStore
import com.example.roy.data.chat.chatConnectivity
import kotlinx.coroutines.flow.Flow
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.data.points.PointRemoteDataSource
import com.example.roy.data.points.CreatePointRemoteDataSource
import com.example.roy.data.points.PointDetailRemoteDataSource
import com.example.roy.domain.map.LoadPoint
import com.example.roy.domain.map.PointRepository
import com.example.roy.domain.map.PointFilterStore
import com.example.roy.domain.points.CreatePointRepository
import com.example.roy.domain.points.PointDetailOperations
import com.example.roy.location.rememberAndroidDeviceLocation
import com.example.roy.presentation.navigation.AuthReturnCoordinator

@Composable
fun RoyAndroidApp(context: Context, origin: String?) {
    val model = viewModel {
        val apiOrigin = origin ?: "https://roy.invalid"
        val transport = AndroidAuthHttpTransport()
        val repository = AuthRemoteDataSource(apiOrigin, transport)
        val session = SessionCoordinator(repository, AndroidSessionStore(context.applicationContext))
        val points = PointRemoteDataSource(apiOrigin, transport)
        val createPoints = CreatePointRemoteDataSource(apiOrigin, transport)
        val preferences = context.applicationContext.getSharedPreferences("roy_map", Context.MODE_PRIVATE)
        val filterStore = FilterPreferencesStore(
            readValue = { preferences.getString("point_filter", null) },
            writeValue = { preferences.edit().putString("point_filter", it).apply() },
        )
        AndroidAppModel(
            apiOrigin,
            transport,
            chatConnectivity(context.applicationContext),
            session,
            points,
            createPoints,
            filterStore,
            PointDetailOperations(
                LoadPoint(points), session, PointDetailRemoteDataSource(apiOrigin, transport), createPoints::getPresets,
            ),
        )
    }
    App(
        model.session, model.points, rememberAndroidDeviceLocation(context), model.continuation,
        model.createPoints, model.pointDetails, model.filterStore, model.origin, model.transport, model.connectivity,
    )
}

private class AndroidAppModel(
    val origin: String,
    val transport: AuthHttpTransport,
    val connectivity: Flow<Boolean>,
    val session: SessionCoordinator,
    val points: PointRepository,
    val createPoints: CreatePointRepository,
    val filterStore: PointFilterStore,
    val pointDetails: PointDetailOperations,
) : ViewModel() {
    val continuation = AuthReturnCoordinator()
}
