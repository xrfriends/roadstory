package com.roadstory.pleos.platform

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/** Activity and service clients create and use this source on the main thread. */
class CarUxRestrictionSource(context: Context) : DrivingRestrictionSource {
    private var manager: CarUxRestrictionsManager? = null
    private var registered = false
    private var closed = false
    private var connectionEpoch = 0L
    private var observer: ((DrivingRestriction) -> Unit)? = null
    private val car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connection, ready ->
        if (!closed) {
            connectionEpoch++
            registered = false
            manager = if (ready) try {
                connection.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            } catch (_: RuntimeException) {
                null
            } else null
            if (observer != null) registerAndReport()
        }
    }

    override fun current(): DrivingRestriction =
        manager?.takeIf { registered }?.currentCarUxRestrictions?.toDomain() ?: DrivingRestriction.RESTRICTED

    override fun observe(listener: (DrivingRestriction) -> Unit) {
        if (closed) return
        observer = listener
        registerAndReport()
    }

    private fun registerAndReport() {
        val connected = manager
        val restriction = try {
            if (connected != null && !registered) {
                val epoch = connectionEpoch
                connected.registerListener { updated ->
                    if (connectionEpoch == epoch && registered) observer?.invoke(updated.toDomain())
                }
                registered = true
            }
            current()
        } catch (_: RuntimeException) {
            // Retain the connection so a later car service reconnect can register a fresh listener.
            DrivingRestriction.RESTRICTED
        }
        observer?.invoke(restriction)
    }

    override fun close() {
        closed = true
        observer = null
        val connected = manager
        manager = null
        try {
            if (registered) connected?.unregisterListener()
        } finally {
            registered = false
            car?.disconnect()
        }
    }

    private fun CarUxRestrictions.toDomain() = DrivingRestriction(
        distractionOptimizationRequired = isRequiresDistractionOptimization,
        activeRestrictions = activeRestrictions,
    )
}

