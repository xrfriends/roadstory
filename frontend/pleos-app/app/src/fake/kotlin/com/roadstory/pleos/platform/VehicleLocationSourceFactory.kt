package com.roadstory.pleos.platform

import android.content.Context

object VehicleLocationSourceFactory {
    fun create(context: Context): VehicleLocationSource {
        val appContext = context.applicationContext
        return object : VehicleLocationSource {
            override fun missingPermissions() = missingAndroidLocationPermissions(appContext)

            override fun request(callback: (VehicleLocationResult) -> Unit) =
                requestVehicleLocation(callback) { complete -> startAndroidLocation(appContext, complete) }
        }
    }
}
