package com.example.roy.data.chat

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun chatConnectivity(context: Context): Flow<Boolean> = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
        send(true)
        close()
        return@callbackFlow
    }
    fun publish() {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = publish()
    }
    manager.registerDefaultNetworkCallback(callback)
    publish()
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
