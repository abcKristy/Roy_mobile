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
    val initial = manager.getNetworkCapabilities(manager.activeNetwork)
    trySend(initial?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    val callback = object : ConnectivityManager.NetworkCallback() {
        private var defaultNetwork: Network? = null

        override fun onAvailable(network: Network) {
            defaultNetwork = network
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (defaultNetwork == network) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        override fun onLost(network: Network) {
            if (defaultNetwork == network) {
                defaultNetwork = null
                trySend(false)
            }
        }
    }
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
