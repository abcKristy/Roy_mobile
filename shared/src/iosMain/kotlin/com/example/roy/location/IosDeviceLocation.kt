package com.example.roy.location

import com.example.roy.domain.location.DeviceLocation
import com.example.roy.domain.location.DeviceLocationResult
import com.example.roy.domain.map.Position
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSError
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
internal class IosDeviceLocation : DeviceLocation {
    override suspend fun locate(): DeviceLocationResult {
        if (!locationServicesEnabled()) return DeviceLocationResult.Disabled
        val result = withContext(Dispatchers.Main.immediate) { requestOnMain() }
        // Core Location also reports "denied" when global Location Services are switched off.
        return if (result !is DeviceLocationResult.Located && !locationServicesEnabled()) {
            DeviceLocationResult.Disabled
        } else {
            result
        }
    }

    private suspend fun locationServicesEnabled(): Boolean =
        withContext(Dispatchers.Default) { CLLocationManager.locationServicesEnabled() }

    private suspend fun requestOnMain(): DeviceLocationResult {
        val manager = CLLocationManager()
        val delegate = LocationRequestDelegate()
        manager.delegate = delegate
        try {
            val initial = manager.authorizationStatus
            val status = if (initial == kCLAuthorizationStatusNotDetermined) {
                manager.requestWhenInUseAuthorization()
                delegate.authorization.await()
            } else {
                initial
            }

            return when (status) {
                kCLAuthorizationStatusAuthorizedAlways, kCLAuthorizationStatusAuthorizedWhenInUse -> {
                    manager.requestLocation()
                    withTimeoutOrNull(20.seconds) { delegate.fix.await() } ?: DeviceLocationResult.Unavailable
                }
                kCLAuthorizationStatusDenied -> if (initial == kCLAuthorizationStatusNotDetermined) {
                    DeviceLocationResult.Denied
                } else {
                    DeviceLocationResult.Restricted
                }
                kCLAuthorizationStatusRestricted -> DeviceLocationResult.Restricted
                else -> DeviceLocationResult.Unavailable
            }
        } finally {
            delegate.detach(manager)
        }
    }

    private class LocationRequestDelegate : NSObject(), CLLocationManagerDelegateProtocol {
        val authorization = CompletableDeferred<CLAuthorizationStatus>()
        val fix = CompletableDeferred<DeviceLocationResult>()

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val status = manager.authorizationStatus
            if (status != kCLAuthorizationStatusNotDetermined) authorization.complete(status)
            if (status == kCLAuthorizationStatusDenied || status == kCLAuthorizationStatusRestricted) {
                fix.complete(DeviceLocationResult.Restricted)
            }
        }

        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation
            val position = location?.takeIf { it.horizontalAccuracy >= 0 }?.coordinate?.useContents {
                if (latitude in -90.0..90.0 && longitude in -180.0..180.0) Position(latitude, longitude) else null
            }
            fix.complete(position?.let { DeviceLocationResult.Located(it) } ?: DeviceLocationResult.Unavailable)
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            val status = manager.authorizationStatus
            val problem = if (status == kCLAuthorizationStatusRestricted || status == kCLAuthorizationStatusDenied) {
                DeviceLocationResult.Restricted
            } else {
                DeviceLocationResult.Unavailable
            }
            fix.complete(problem)
        }

        fun detach(manager: CLLocationManager) {
            // CLLocationManager's delegate is weak. Retain this receiver through the whole request.
            manager.delegate = null
            manager.stopUpdatingLocation()
            authorization.cancel()
            fix.cancel()
        }
    }
}
