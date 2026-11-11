package com.roadstory.pleos.platform

import android.content.Context

object NavigationGatewayFactory {
    fun create(context: Context): NavigationGateway = UnavailableNavigationGateway()
}

private class UnavailableNavigationGateway : NavigationGateway {
    override fun current() = VehicleNavigationState.Unavailable
    override fun observe(listener: (VehicleNavigationState) -> Unit): AutoCloseable {
        listener(current())
        return AutoCloseable {}
    }

    override fun refresh() = Unit
    override fun requestFirstStop(latitude: Double, longitude: Double, name: String, callback: (Boolean) -> Unit) {
        callback(false)
    }

    override fun close() = Unit
}
