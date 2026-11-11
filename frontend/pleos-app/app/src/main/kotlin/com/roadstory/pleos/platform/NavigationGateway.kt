package com.roadstory.pleos.platform

/** Snapshot of the current route in the vehicle navigation app. Coordinates are transient. */
data class NavigationDestination(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val remainingDurationSeconds: Int?,
) {
    override fun toString() = "NavigationDestination(remainingDurationSeconds=$remainingDurationSeconds)"
}

sealed interface VehicleNavigationState {
    data object Unavailable : VehicleNavigationState
    data object Loading : VehicleNavigationState
    data object NoDestination : VehicleNavigationState
    data class Guiding(val destination: NavigationDestination, val routeRevision: Long) : VehicleNavigationState
}

/** Create, call and close on the main thread. Listener and request results arrive on the main thread. */
interface NavigationGateway : AutoCloseable {
    fun current(): VehicleNavigationState
    fun observe(listener: (VehicleNavigationState) -> Unit): AutoCloseable
    fun refresh()

    /** Only for an explicitly confirmed parked action. Reports whether navigation confirmed the stop. */
    fun requestFirstStop(latitude: Double, longitude: Double, name: String, callback: (Boolean) -> Unit)
}
