package com.roadstory.pleos.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager

object VehicleLocationSourceFactory {
    private const val pleosPermission = "pleos.car.permission.FUSED_LOCATION"

    fun create(context: Context): VehicleLocationSource {
        val appContext = context.applicationContext
        return object : VehicleLocationSource {
            override fun missingPermissions(): List<String> {
                val supported = try {
                    FusedLocation(appContext).isSupported()
                } catch (_: RuntimeException) {
                    return emptyList()
                }
                return if (supported) missingPleosPermissions(appContext) else missingAndroidLocationPermissions(appContext)
            }

            override fun request(callback: (VehicleLocationResult) -> Unit) =
                requestVehicleLocation(callback) { complete ->
                    val fused = FusedLocation(appContext)
                    if (fused.isSupported()) startPleosLocation(appContext, fused, complete)
                    else startAndroidLocation(appContext, complete)
                }
        }
    }

    private fun missingPleosPermissions(context: Context): List<String> =
        if (context.checkSelfPermission(pleosPermission) == PackageManager.PERMISSION_GRANTED) emptyList()
        else listOf(pleosPermission)

    private fun startPleosLocation(
        context: Context,
        fused: FusedLocation,
        complete: (VehicleLocationResult) -> Unit,
    ): AutoCloseable? {
        val permissionRequired = VehicleLocationResult.PermissionRequired(listOf(pleosPermission))
        if (missingPleosPermissions(context).isNotEmpty()) {
            complete(permissionRequired)
            return null
        }

        fun listener(onFailure: () -> Unit) = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                if (missingPleosPermissions(context).isNotEmpty()) {
                    complete(permissionRequired)
                    return
                }
                if (location == null || location.timestampMillis <= 0) return
                val fix = VehicleLocationQuality.fromPleos(
                    location.latitude,
                    location.longitude,
                    System.currentTimeMillis() - location.timestampMillis,
                    location.confidence,
                    location.heading.toDouble(),
                )
                if (fix != null) complete(VehicleLocationResult.Available(fix))
            }

            override fun onError(exception: Exception) {
                if (context.checkSelfPermission(pleosPermission) != PackageManager.PERMISSION_GRANTED) {
                    complete(permissionRequired)
                } else {
                    onFailure()
                }
            }
        }

        val live = listener { complete(VehicleLocationResult.Unavailable) }
        val cleanup = AutoCloseable {
            try {
                fused.unregisterFusedLocationCallback(live)
            } finally {
                fused.release()
            }
        }
        try {
            fused.initialize()
            fused.registerFusedLocationCallback(live)
            fused.getLastKnownLocation(listener {})
        } catch (exception: RuntimeException) {
            try {
                cleanup.close()
            } catch (_: RuntimeException) {
                // Report the lookup error and keep platform details out of logs.
            }
            if (exception is SecurityException) complete(permissionRequired)
            else complete(VehicleLocationResult.Unavailable)
            return null
        }
        return cleanup
    }
}
