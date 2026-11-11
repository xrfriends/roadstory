package com.roadstory.pleos.platform

import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.vehicle.Vehicle
import android.content.Context

object PlatformRuntimeFactory {
    fun create(context: Context): PleosRuntimeGateway = PleosSdkRuntimeGateway(context.applicationContext)
}

private class PleosSdkRuntimeGateway(context: Context) : PleosRuntimeGateway {
    private val vehicle by lazy { Vehicle(context) }
    private val naviHelper by lazy { NaviHelper(context) }
    private var vehicleInitialized = false
    private var navigationInitialized = false

    override fun initializeVehicleCapabilityCheck() {
        if (!vehicleInitialized) {
            vehicle.initialize()
            vehicleInitialized = true
        }
    }

    override fun initializeNavigationEvents() {
        if (!navigationInitialized) {
            naviHelper.initialize()
            navigationInitialized = true
        }
    }

    override fun close() {
        if (navigationInitialized) naviHelper.release()
        if (vehicleInitialized) vehicle.release()
    }
}
