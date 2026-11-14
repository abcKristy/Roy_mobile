package com.example.roy.shared

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roy.App
import com.example.roy.data.auth.AndroidAuthHttpTransport
import com.example.roy.data.auth.AndroidSessionStore
import com.example.roy.data.auth.AuthRemoteDataSource
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.data.points.PointRemoteDataSource
import com.example.roy.domain.map.PointRepository
import com.example.roy.location.rememberAndroidDeviceLocation

@Composable
fun RoyAndroidApp(context: Context, origin: String?) {
    val model = viewModel {
        val apiOrigin = origin ?: "https://roy.invalid"
        val transport = AndroidAuthHttpTransport()
        val repository = AuthRemoteDataSource(apiOrigin, transport)
        AndroidAppModel(
            SessionCoordinator(repository, AndroidSessionStore(context.applicationContext)),
            PointRemoteDataSource(apiOrigin, transport),
        )
    }
    App(model.session, model.points, rememberAndroidDeviceLocation(context))
}

private class AndroidAppModel(val session: SessionCoordinator, val points: PointRepository) : ViewModel()
