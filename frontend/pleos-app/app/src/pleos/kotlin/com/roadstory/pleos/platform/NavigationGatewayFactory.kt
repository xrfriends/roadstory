package com.roadstory.pleos.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.RouteDriving
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper

object NavigationGatewayFactory {
    fun create(context: Context): NavigationGateway = PleosNavigationGateway(context.applicationContext)
}

private class PleosNavigationGateway(private val context: Context) : NavigationGateway {
    private enum class QueryType { ROUTE_STATE, DESTINATION }
    private data class Query(val type: QueryType, val generation: Long)
    private class StopRequest(val latitude: Double, val longitude: Double, val callback: (Boolean) -> Unit)

    private val handler = Handler(Looper.getMainLooper())
    private val observers = mutableSetOf<(VehicleNavigationState) -> Unit>()
    private var sdk: NaviHelper? = null
    private var state: VehicleNavigationState = VehicleNavigationState.Loading
    private var lastDestination: NavigationDestination? = null
    private var revision = 0L
    private var generation = 0L
    private var query: Query? = null
    private var request: StopRequest? = null
    private var closed = false

    private val queryTimeout = Runnable {
        val timedOut = query
        query = null
        if (timedOut?.generation == generation) publish(VehicleNavigationState.Unavailable)
        else if (state == VehicleNavigationState.Loading) refresh()
    }
    private val stopTimeout = Runnable { completeStop(false) }
    private val checkRoute = Runnable { refresh() }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = onMain {
            if (!consumeQuery(QueryType.ROUTE_STATE)) return@onMain
            if (!routeStateInfo.termsAgreement) {
                publish(VehicleNavigationState.Unavailable)
                completeStop(false)
            } else if (routeStateInfo.isRouting || routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING ||
                routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
            ) {
                beginQuery(QueryType.DESTINATION, NaviHelper::getDestinationInfo)
            } else {
                lastDestination = null
                publish(VehicleNavigationState.NoDestination)
            }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) = onMain {
            if (!consumeQuery(QueryType.DESTINATION)) return@onMain
            val latitude = destinationInfo.latitude
            val longitude = destinationInfo.longitude
            if (!validCoordinates(latitude, longitude)) {
                lastDestination = null
                publish(VehicleNavigationState.NoDestination)
                return@onMain
            }
            // NaviHelper 2.0.3 uses navi-data 2.0.0.1, whose DestinationInfo specifies seconds.
            val destination = NavigationDestination(
                destinationInfo.name.orEmpty().trim().ifEmpty { "내비게이션 목적지" }, latitude, longitude,
                destinationInfo.totalTime.takeIf { it > 0 },
            )
            val previous = lastDestination
            if (previous != null && (latitude != previous.latitude || longitude != previous.longitude)) revision++
            lastDestination = destination
            publish(VehicleNavigationState.Guiding(destination, revision))
            if (request?.let { matchesStop(destination, it.latitude, it.longitude) } == true) completeStop(true)
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = onMain {
            invalidateRoute()
            scheduleRefresh(ROUTE_UPDATE_DELAY_MS)
        }

        override fun onRouteEnded() = onMain { routeChanged() }
        override fun onReRouteEnded() = onMain { routeChanged() }
        override fun onDestinationChanged() = onMain { routeChanged() }
        override fun onRouteCancelled() = onMain { endRoute(); completeStop(false) }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = onMain { endRoute() }
        override fun onNavigationStatus(status: RouteDriving) = onMain {
            if (status == RouteDriving.START) routeChanged() else endRoute()
        }

        override fun onError(errorCode: NaviErrorCode) = onMain {
            generation++
            query = null
            lastDestination = null
            handler.removeCallbacks(queryTimeout)
            publish(VehicleNavigationState.Unavailable)
            completeStop(false)
        }
    }

    override fun current() = state

    override fun observe(listener: (VehicleNavigationState) -> Unit): AutoCloseable {
        if (closed) {
            listener(VehicleNavigationState.Unavailable)
            return AutoCloseable {}
        }
        val first = observers.isEmpty()
        observers.add(listener)
        listener(state)
        if (first) refresh()
        return AutoCloseable {
            observers.remove(listener)
            if (observers.isEmpty() && request == null) handler.removeCallbacks(checkRoute)
        }
    }

    override fun refresh() {
        if (closed || query != null) return
        if (!hasPermission(NAVI_ROUTE)) {
            publish(VehicleNavigationState.Unavailable)
            return
        }
        if (sdk == null) {
            try {
                sdk = NaviHelper(context)
                sdk?.initialize()
                sdk?.addListener(listener)
            } catch (_: RuntimeException) {
                runCatching { sdk?.release() }
                sdk = null
                publish(VehicleNavigationState.Unavailable)
                return
            }
        }
        beginQuery(QueryType.ROUTE_STATE, NaviHelper::getRouteStateInfo)
    }

    override fun requestFirstStop(latitude: Double, longitude: Double, name: String, callback: (Boolean) -> Unit) {
        val navi = sdk
        if (closed || navi == null || request != null || !hasPermission(NAVI_ROUTE_SEARCH) ||
            state == VehicleNavigationState.Unavailable || !validCoordinates(latitude, longitude) || name.isBlank()
        ) {
            callback(false)
            return
        }
        val destination = (state as? VehicleNavigationState.Guiding)?.destination
        if (destination != null && matchesStop(destination, latitude, longitude)) {
            callback(true)
            return
        }
        request = StopRequest(latitude, longitude, callback)
        handler.postDelayed(stopTimeout, STOP_TIMEOUT_MS)
        try {
            // Course stops have coordinates and a label. The provider decides whether it can route to them.
            navi.requestRoute(RouteInfo(longitude = longitude, latitude = latitude, poiName = name,
                poiId = "", address = "", poiSubId = ""))
            routeChanged()
        } catch (_: RuntimeException) {
            completeStop(false)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        observers.clear()
        query = null
        completeStop(false)
        sdk?.let { navi ->
            runCatching { navi.removeListener(listener) }
            runCatching { navi.release() }
        }
        sdk = null
        lastDestination = null
        state = VehicleNavigationState.Unavailable
    }

    private fun onMain(action: () -> Unit) {
        handler.post { if (!closed) action() }
    }

    private fun beginQuery(type: QueryType, action: NaviHelper.() -> Unit) {
        val navi = sdk ?: return
        query = Query(type, generation)
        handler.postDelayed(queryTimeout, QUERY_TIMEOUT_MS)
        try {
            navi.action()
        } catch (_: RuntimeException) {
            query = null
            handler.removeCallbacks(queryTimeout)
            publish(VehicleNavigationState.Unavailable)
            completeStop(false)
        }
    }

    private fun consumeQuery(type: QueryType): Boolean {
        val pending = query ?: return false
        if (pending.type != type) return false
        query = null
        handler.removeCallbacks(queryTimeout)
        if (pending.generation == generation) return true
        if (state == VehicleNavigationState.Loading) scheduleRefresh(ROUTE_UPDATE_DELAY_MS)
        return false
    }

    private fun routeChanged() {
        invalidateRoute()
        scheduleRefresh(ROUTE_UPDATE_DELAY_MS)
    }

    private fun invalidateRoute() {
        generation++
        if (state != VehicleNavigationState.Loading) revision++
        publish(VehicleNavigationState.Loading)
    }

    private fun endRoute() {
        generation++
        revision++
        lastDestination = null
        publish(VehicleNavigationState.NoDestination)
    }

    private fun publish(updated: VehicleNavigationState) {
        if (updated != state) {
            state = updated
            observers.toList().forEach { it(updated) }
        }
        if (updated is VehicleNavigationState.Guiding) scheduleRefresh(ACTIVE_REFRESH_MS)
        else handler.removeCallbacks(checkRoute)
    }

    private fun scheduleRefresh(delayMs: Long) {
        handler.removeCallbacks(checkRoute)
        if (!closed && (observers.isNotEmpty() || request != null)) handler.postDelayed(checkRoute, delayMs)
    }

    private fun completeStop(success: Boolean) {
        val pending = request ?: return
        request = null
        handler.removeCallbacks(stopTimeout)
        pending.callback(success)
    }

    private fun hasPermission(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun validCoordinates(latitude: Double, longitude: Double) =
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && (latitude != 0.0 || longitude != 0.0)

    private fun matchesStop(destination: NavigationDestination, latitude: Double, longitude: Double): Boolean {
        val distance = FloatArray(1)
        Location.distanceBetween(destination.latitude, destination.longitude, latitude, longitude, distance)
        return distance[0] <= STOP_MATCH_DISTANCE_METERS
    }

    private companion object {
        const val NAVI_ROUTE = "pleos.car.permission.NAVI_ROUTE"
        const val NAVI_ROUTE_SEARCH = "pleos.car.permission.NAVI_ROUTE_SEARCH"
        const val QUERY_TIMEOUT_MS = 5_000L
        const val STOP_TIMEOUT_MS = 15_000L
        const val ROUTE_UPDATE_DELAY_MS = 350L
        const val ACTIVE_REFRESH_MS = 15_000L
        const val STOP_MATCH_DISTANCE_METERS = 100f
    }
}
