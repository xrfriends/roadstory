package com.roadstory.pleos.platform

import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NavigationGatewayDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun reportsTheStateFromTheInstalledNaviService() {
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission("pleos.car.permission.NAVI_ROUTE"))
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission("pleos.car.permission.NAVI_ROUTE_SEARCH"))
        val route = checkNotNull(readNaviRoute()) { "NaviHelper did not deliver its route state callback" }
        val expected = when {
            !route.termsAgreement -> VehicleNavigationState.Unavailable::class.java
            route.hasActiveRoute() -> VehicleNavigationState.Guiding::class.java
            else -> VehicleNavigationState.NoDestination::class.java
        }

        val updates = LinkedBlockingQueue<Class<out VehicleNavigationState>>()
        var gateway: NavigationGateway? = null
        var subscription: AutoCloseable? = null
        try {
            instrumentation.runOnMainSync {
                gateway = NavigationGatewayFactory.create(context).also {
                    subscription = it.observe { state -> updates.offer(state.javaClass) }
                }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
            var matched = false
            while (!matched) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) break
                matched = updates.poll(remaining, TimeUnit.NANOSECONDS) == expected
            }
            assertTrue("Gateway did not publish the installed NaviService state (${expected.simpleName})", matched)
        } finally {
            instrumentation.runOnMainSync {
                subscription?.close()
                gateway?.close()
            }
        }
    }

    /** Run explicitly on a parked test emulator with no existing navigation route. */
    @Test
    fun firstCourseStopConfirmsRealGuidanceAndCleansUp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyNaviRoute") == "true")
        val baseline = readNaviRoute()
        assumeTrue("Navi must have accepted terms, be ready, and have no active route",
            baseline?.termsAgreement == true && !baseline.hasActiveRoute() && baseline.naviMode == NaviMode.SAFE_GUIDING)

        val restrictions = FailClosedDrivingRestrictionSource { CarUxRestrictionSource(context) }
        val parked = CountDownLatch(1)
        val idle = CountDownLatch(1)
        val replies = LinkedBlockingQueue<Boolean>()
        val label = "성수역 Road Story 테스트 ${UUID.randomUUID().toString().take(8)}"
        var gateway: NavigationGateway? = null
        var subscription: AutoCloseable? = null
        var submitted = false
        var ownedRoute: NavigationDestination? = null
        try {
            instrumentation.runOnMainSync {
                restrictions.observe { if (!it.blocksDetailedContent) parked.countDown() }
                gateway = NavigationGatewayFactory.create(context).also {
                    subscription = it.observe { state -> if (state == VehicleNavigationState.NoDestination) idle.countDown() }
                }
            }
            assumeTrue("PLEOS must be parked", parked.await(10, TimeUnit.SECONDS))
            assumeTrue("The app must confirm there is no destination", idle.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                val navigation = checkNotNull(gateway)
                if (!restrictions.current().blocksDetailedContent && navigation.current() == VehicleNavigationState.NoDestination) {
                    submitted = true
                    navigation.requestFirstStop(SEONGSU_LATITUDE, SEONGSU_LONGITUDE, label) { replies.offer(it) }
                }
            }
            assumeTrue("Parked and no-route checks must still hold when sending the request", submitted)
            val confirmed = replies.poll(20, TimeUnit.SECONDS) == true
            instrumentation.runOnMainSync {
                val guiding = gateway?.current() as? VehicleNavigationState.Guiding
                ownedRoute = guiding?.destination?.takeIf {
                    nearSeongsuStation(it.latitude, it.longitude) && (confirmed || it.name == label)
                }
            }
            assertTrue("Navi did not confirm guidance to the test stop", confirmed && ownedRoute != null)
            assertTrue("Navi did not provide a positive travel-time estimate in seconds",
                (ownedRoute?.remainingDurationSeconds ?: 0) > 0)
        } finally {
            instrumentation.runOnMainSync {
                subscription?.close()
                gateway?.close()
            }
            try {
                if (submitted) cancelOwnedRoute(label, ownedRoute, restrictions)
            } finally {
                instrumentation.runOnMainSync { restrictions.close() }
            }
        }
    }

    private fun cancelOwnedRoute(
        label: String,
        confirmed: NavigationDestination?,
        restrictions: DrivingRestrictionSource,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val destinations = LinkedBlockingQueue<DestinationInfo>()
        val states = LinkedBlockingQueue<RouteStateInfo>()
        val cancelled = CountDownLatch(1)
        val navi = NaviHelper(context)
        val listener = object : NaviHelperEventListener {
            override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                destinations.offer(destinationInfo)
            }

            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                states.offer(routeStateInfo)
            }

            override fun onRouteCancelled() {
                cancelled.countDown()
            }
        }
        var sentCancellation = false
        try {
            instrumentation.runOnMainSync {
                navi.initialize()
                navi.addListener(listener)
                handler.postDelayed({ navi.getRouteStateInfo() }, 500L)
            }
            val route = states.poll(5, TimeUnit.SECONDS)
            if (route == null) {
                assertTrue("Could not recheck the confirmed test route", confirmed == null)
                return
            }
            if (!route.hasActiveRoute()) return
            instrumentation.runOnMainSync { navi.getDestinationInfo() }
            val current = destinations.poll(5, TimeUnit.SECONDS)
            if (current == null) {
                assertTrue("Could not recheck the confirmed test destination", confirmed == null)
                return
            }
            val matchesConfirmed = confirmed != null && current.name.orEmpty().trim() == confirmed.name &&
                current.latitude == confirmed.latitude && current.longitude == confirmed.longitude
            if (!nearSeongsuStation(current.latitude, current.longitude) ||
                (!matchesConfirmed && current.name != label)
            ) return
            instrumentation.runOnMainSync {
                if (!restrictions.current().blocksDetailedContent) {
                    navi.cancelRoute()
                    sentCancellation = true
                }
            }
            assertTrue("The matching test route could not be cancelled while parked", sentCancellation)
            if (!cancelled.await(5, TimeUnit.SECONDS)) {
                states.clear()
                instrumentation.runOnMainSync { navi.getRouteStateInfo() }
                assertTrue("Navi did not confirm test route cleanup", states.poll(5, TimeUnit.SECONDS)?.hasActiveRoute() == false)
            }
        } finally {
            instrumentation.runOnMainSync {
                handler.removeCallbacksAndMessages(null)
                navi.removeListener(listener)
                navi.release()
            }
        }
    }

    private fun RouteStateInfo.hasActiveRoute() =
        isRouting || naviMode == NaviMode.ROUTE_GUIDING || naviMode == NaviMode.SIMULATION_GUIDING

    private fun nearSeongsuStation(latitude: Double, longitude: Double): Boolean {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        val distance = FloatArray(1)
        Location.distanceBetween(latitude, longitude, SEONGSU_LATITUDE, SEONGSU_LONGITUDE, distance)
        return distance[0] <= 100f
    }

    private fun readNaviRoute(): RouteStateInfo? {
        val handler = Handler(Looper.getMainLooper())
        val replies = LinkedBlockingQueue<RouteStateInfo>()
        val navi = NaviHelper(context)
        val listener = object : NaviHelperEventListener {
            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                replies.offer(routeStateInfo)
            }
        }
        return try {
            instrumentation.runOnMainSync {
                navi.initialize()
                navi.addListener(listener)
                handler.postDelayed({ navi.getRouteStateInfo() }, 500L)
            }
            replies.poll(10, TimeUnit.SECONDS)
        } finally {
            instrumentation.runOnMainSync {
                handler.removeCallbacksAndMessages(null)
                navi.removeListener(listener)
                navi.release()
            }
        }
    }

    private companion object {
        // Public coordinate for the same Seongsu Station stop as the published course (서울 열린데이터 OA-22534).
        const val SEONGSU_LATITUDE = 37.544628
        const val SEONGSU_LONGITUDE = 127.055983
    }
}
