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

@Composable
fun RoyAndroidApp(context: Context, origin: String?) {
    val model = viewModel {
        val repository = AuthRemoteDataSource(origin ?: "https://roy.invalid", AndroidAuthHttpTransport())
        AndroidAppModel(SessionCoordinator(repository, AndroidSessionStore(context.applicationContext)))
    }
    App(model.session)
}

private class AndroidAppModel(val session: SessionCoordinator) : ViewModel()
