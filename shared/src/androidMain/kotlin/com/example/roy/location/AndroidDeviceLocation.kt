package com.example.roy.location

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.roy.domain.location.DeviceLocation
import com.example.roy.domain.location.DeviceLocationResult
import com.example.roy.domain.map.Position
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal fun rememberAndroidDeviceLocation(context: Context): DeviceLocation {
    val activity = LocalContext.current.findActivity()
    val request = remember(activity) { LocationPermissionRequest() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> request.onResult(results.isNotEmpty()) }

    DisposableEffect(request) { onDispose { request.cancel() } }

    return remember(context, activity, request, launcher) {
        DeviceLocation {
            val manager = context.getSystemService(LocationManager::class.java)
            when {
                manager == null || activity == null -> DeviceLocationResult.Unavailable
                !LocationManagerCompat.isLocationEnabled(manager) -> DeviceLocationResult.Disabled
                else -> {
                    val permissionCompleted = context.hasLocationPermission() || request.await {
                        launcher.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
                    }
                    when {
                        context.hasLocationPermission() -> currentLocation(context, manager)
                        !permissionCompleted ||
                            activity.shouldShowRequestPermissionRationale(ACCESS_COARSE_LOCATION) ||
                            activity.shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION) ->
                            DeviceLocationResult.Denied
                        else -> DeviceLocationResult.Restricted
                    }
                }
            }
        }
    }
}

/** Keep the OS request in flight even if the caller leaves and another request arrives. */
private class LocationPermissionRequest {
    private var inFlight: CompletableDeferred<Boolean>? = null

    suspend fun await(launch: () -> Unit): Boolean {
        val existing = inFlight
        if (existing != null) return existing.await()

        val result = CompletableDeferred<Boolean>()
        inFlight = result
        try {
            launch()
        } catch (_: IllegalStateException) {
            onResult(false)
        }
        return result.await()
    }

    fun onResult(completed: Boolean) {
        val result = inFlight
        inFlight = null
        result?.complete(completed)
    }

    fun cancel() {
        inFlight?.cancel()
        inFlight = null
    }
}

private suspend fun currentLocation(context: Context, manager: LocationManager): DeviceLocationResult {
    if (!LocationManagerCompat.isLocationEnabled(manager)) return DeviceLocationResult.Disabled

    val enabled = manager.getProviders(true)
    val isPrecise = context.hasPermission(ACCESS_FINE_LOCATION)
    val providers = buildList {
        if (isPrecise && LocationManager.GPS_PROVIDER in enabled) add(LocationManager.GPS_PROVIDER)
        if (LocationManager.FUSED_PROVIDER in enabled) add(LocationManager.FUSED_PROVIDER)
        if (isEmpty() && LocationManager.NETWORK_PROVIDER in enabled) add(LocationManager.NETWORK_PROVIDER)
    }
    if (providers.isEmpty()) return DeviceLocationResult.Unavailable

    val location = try {
        withTimeoutOrNull(15_000) {
            coroutineScope {
                val fixes = Channel<Location?>(providers.size)
                val requests = providers.map { provider ->
                    launch {
                        val fix = try {
                            requestLocation(context, manager, provider)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                        fixes.send(fix)
                    }
                }
                try {
                    repeat(providers.size) {
                        fixes.receive()?.let { return@coroutineScope it }
                    }
                    null
                } finally {
                    requests.forEach { it.cancel() }
                    fixes.cancel()
                }
            }
        }
    } catch (_: SecurityException) {
        return DeviceLocationResult.Restricted
    }

    return when {
        !LocationManagerCompat.isLocationEnabled(manager) -> DeviceLocationResult.Disabled
        location == null -> DeviceLocationResult.Unavailable
        else -> DeviceLocationResult.Located(Position(latitude = location.latitude, longitude = location.longitude))
    }
}

private suspend fun requestLocation(
    context: Context,
    manager: LocationManager,
    provider: String,
): Location? = suspendCancellableCoroutine { continuation ->
    val signal = CancellationSignal()
    continuation.invokeOnCancellation { signal.cancel() }
    try {
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) {
            continuation.resume(it)
        }
    } catch (error: SecurityException) {
        continuation.resumeWithException(error)
    } catch (error: IllegalArgumentException) {
        continuation.resumeWithException(error)
    }
}

private fun Context.hasLocationPermission() =
    hasPermission(ACCESS_COARSE_LOCATION) || hasPermission(ACCESS_FINE_LOCATION)

private fun Context.hasPermission(permission: String) = checkSelfPermission(permission) == PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
