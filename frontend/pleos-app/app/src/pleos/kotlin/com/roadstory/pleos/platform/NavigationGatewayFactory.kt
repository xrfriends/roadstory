package com.roadstory.pleos.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
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
    private var sdkListener: NaviHelperEventListener? = null
    private var state: VehicleNavigationState = VehicleNavigationState.Loading
    private var lastDestination: NavigationDestination? = null
    private var revision = 0L
    private var generation = 0L
    private var query: Query? = null
    private var request: StopRequest? = null
    private var closed = false

    private val queryTimeout = Runnable {
        val timedOut = query ?: return@Runnable
        query = null
        // SDK responses carry no request ID. A timed-out client must not answer a later query.
        releaseSdk()
        if (timedOut.generation == generation) {
            publish(VehicleNavigationState.Unavailable)
            completeStop(false)
        } else if (observers.isNotEmpty() || request != null) {
            refresh()
        }
    }
    private val stopTimeout = Runnable { completeStop(false) }
    private val checkRoute = Runnable { refresh() }

    private fun createListener(navi: NaviHelper) = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = onSdk(navi) {
            if (!consumeQuery(QueryType.ROUTE_STATE)) return@onSdk
            if (!routeStateInfo.termsAgreement || routeStateInfo.naviMode == NaviMode.NOT_WORKING) {
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

        override fun onDestinationInfo(destinationInfo: DestinationInfo) = onSdk(navi) {
            if (!consumeQuery(QueryType.DESTINATION)) return@onSdk
            val latitude = destinationInfo.latitude
            val longitude = destinationInfo.longitude
            if (!validCoordinates(latitude, longitude)) {
                lastDestination = null
                publish(VehicleNavigationState.NoDestination)
                return@onSdk
            }
            val destination = NavigationDestination(
                destinationInfo.name.orEmpty().trim().ifEmpty { "내비게이션 목적지" }, latitude, longitude,
                destinationInfo.remainTime.takeIf { it > 0 },
            )
            val previous = lastDestination
            if (previous != null && (latitude != previous.latitude || longitude != previous.longitude)) revision++
            lastDestination = destination
            publish(VehicleNavigationState.Guiding(destination, revision))
            if (request?.let { matchesStop(destination, it.latitude, it.longitude) } == true) completeStop(true)
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = onSdk(navi) { routeChanged() }
        override fun onRouteEnded() = onSdk(navi) { routeChanged() }
        override fun onRoutingCompleted() = onSdk(navi) { routeChanged() }
        override fun onReRouteEnded() = onSdk(navi) { routeChanged() }
        override fun onDestinationChanged() = onSdk(navi) { routeChanged() }
        override fun onRouteCancelled() = onSdk(navi) { endRoute(); completeStop(false) }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = onSdk(navi) { endRoute() }
        override fun onNavigationStatus(status: NavigationStatus) = onSdk(navi) {
            if (status == NavigationStatus.FREE_DRIVE) endRoute() else routeChanged()
        }

        override fun onError(errorCode: NaviErrorCode) = onSdk(navi) { failNavigation() }
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
            connect()
            return
        }
        if (sdkListener != null) beginQuery(QueryType.ROUTE_STATE, NaviHelper::getRouteStateInfo)
    }

    override fun requestFirstStop(latitude: Double, longitude: Double, name: String, callback: (Boolean) -> Unit) {
        val navi = sdk
        if (closed || navi == null || sdkListener == null || request != null || !hasPermission(NAVI_ROUTE_SEARCH) ||
            (state != VehicleNavigationState.NoDestination && state !is VehicleNavigationState.Guiding) ||
            !validCoordinates(latitude, longitude) || name.isBlank()
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
            navi.requestRoute(RouteInfo(latitude = latitude, longitude = longitude, displayLatitude = null,
                displayLongitude = null, poiName = name, poiId = "", address = "", poiSubId = ""))
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
        releaseSdk()
        lastDestination = null
        state = VehicleNavigationState.Unavailable
    }

    private fun connect() {
        try {
            val navi = NaviHelper(context)
            sdk = navi
            publish(VehicleNavigationState.Loading)
            if (sdk !== navi) return
            navi.initialize(
                onConnected = {
                    onSdk(navi) {
                        if (sdkListener == null) {
                            try {
                                val listener = createListener(navi)
                                navi.addListener(listener)
                                sdkListener = listener
                                refresh()
                            } catch (_: RuntimeException) {
                                failNavigation()
                            }
                        }
                    }
                },
                onFailed = { onSdk(navi) { failNavigation() } },
            )
        } catch (_: RuntimeException) {
            failNavigation()
        }
    }

    private fun releaseSdk() {
        val navi = sdk ?: return
        sdk = null
        val listener = sdkListener
        sdkListener = null
        if (listener != null) runCatching { navi.removeListener(listener) }
        runCatching { navi.release() }
    }

    private fun failNavigation() {
        generation++
        query = null
        lastDestination = null
        handler.removeCallbacks(queryTimeout)
        releaseSdk()
        publish(VehicleNavigationState.Unavailable)
        completeStop(false)
    }

    private fun onSdk(navi: NaviHelper, action: () -> Unit) {
        handler.post { if (!closed && sdk === navi) action() }
    }

    private fun beginQuery(type: QueryType, action: NaviHelper.() -> Unit) {
        val navi = sdk ?: return
        query = Query(type, generation)
        handler.postDelayed(queryTimeout, QUERY_TIMEOUT_MS)
        try {
            navi.action()
        } catch (_: RuntimeException) {
            failNavigation()
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
