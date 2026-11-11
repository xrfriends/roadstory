package com.roadstory.pleos.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

data class VehicleLocationFix(
    val latitude: Double,
    val longitude: Double,
    val heading: Double? = null,
    val speedMps: Double? = null,
) {
    override fun toString(): String = "VehicleLocationFix"
}

sealed interface VehicleLocationResult {
    data class Available(val fix: VehicleLocationFix) : VehicleLocationResult
    data class PermissionRequired(val permissions: List<String>) : VehicleLocationResult
    data object Unavailable : VehicleLocationResult
}

interface VehicleLocationSource {
    fun missingPermissions(): List<String>

    /** Delivers one result on the main thread unless the caller closes the request. */
    fun request(callback: (VehicleLocationResult) -> Unit): AutoCloseable
}

internal object VehicleLocationQuality {
    private const val maxAgeMillis = 30_000L
    private const val maxAccuracyMeters = 100f
    private const val minPleosConfidence = 0.5f

    fun fromAndroid(
        latitude: Double,
        longitude: Double,
        ageMillis: Long,
        accuracyMeters: Float?,
        heading: Double?,
        speedMps: Double?,
    ): VehicleLocationFix? {
        if (accuracyMeters == null || accuracyMeters !in 0f..maxAccuracyMeters) return null
        return acceptedFix(latitude, longitude, ageMillis, heading, speedMps)
    }

    fun fromPleos(
        latitude: Double,
        longitude: Double,
        ageMillis: Long,
        confidence: Float,
        heading: Double?,
    ): VehicleLocationFix? {
        if (!confidence.isFinite() || confidence < minPleosConfidence) return null
        return acceptedFix(latitude, longitude, ageMillis, heading, null)
    }

    private fun acceptedFix(
        latitude: Double,
        longitude: Double,
        ageMillis: Long,
        heading: Double?,
        speedMps: Double?,
    ): VehicleLocationFix? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || ageMillis !in 0L..maxAgeMillis) return null
        return VehicleLocationFix(
            latitude,
            longitude,
            heading?.takeIf { it >= 0.0 && it < 360.0 },
            speedMps?.takeIf { it.isFinite() && it >= 0.0 },
        )
    }
}

/** Owns the timeout and cleanup for a location lookup. All request state stays on the main thread. */
internal fun requestVehicleLocation(
    callback: (VehicleLocationResult) -> Unit,
    start: ((VehicleLocationResult) -> Unit) -> AutoCloseable?,
): AutoCloseable {
    val main = Handler(Looper.getMainLooper())
    var active = true
    var provider: AutoCloseable? = null

    fun close() {
        if (!active) return
        active = false
        main.removeCallbacksAndMessages(null)
        try {
            provider?.close()
        } catch (_: RuntimeException) {
            // The completed request no longer accepts any provider callback.
        }
        provider = null
    }

    fun complete(result: VehicleLocationResult) {
        if (active) {
            close()
            callback(result)
        }
    }

    main.post {
        if (active) {
            main.postDelayed({ complete(VehicleLocationResult.Unavailable) }, 8_000)
            provider = try {
                start { result -> main.post { complete(result) } }
            } catch (_: RuntimeException) {
                complete(VehicleLocationResult.Unavailable)
                null
            }
        }
    }
    return AutoCloseable {
        if (Looper.myLooper() == main.looper) close() else main.post(::close)
    }
}

internal fun missingAndroidLocationPermissions(context: Context): List<String> =
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        emptyList()
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

internal fun startAndroidLocation(
    context: Context,
    complete: (VehicleLocationResult) -> Unit,
): AutoCloseable? {
    val permissions = missingAndroidLocationPermissions(context)
    if (permissions.isNotEmpty()) {
        complete(VehicleLocationResult.PermissionRequired(permissions))
        return null
    }
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager == null || !manager.isLocationEnabled || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        complete(VehicleLocationResult.Unavailable)
        return null
    }
    val cancellation = CancellationSignal()
    try {
        manager.getCurrentLocation(LocationManager.GPS_PROVIDER, cancellation, context.mainExecutor) { location ->
            val elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            val fix = location?.takeIf { it.elapsedRealtimeNanos in 1L..elapsedRealtimeNanos }?.let {
                val ageMillis = (elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1_000_000L
                VehicleLocationQuality.fromAndroid(
                    it.latitude,
                    it.longitude,
                    ageMillis,
                    if (it.hasAccuracy()) it.accuracy else null,
                    if (it.hasBearing()) it.bearing.toDouble() else null,
                    if (it.hasSpeed()) it.speed.toDouble() else null,
                )
            }
            val missing = missingAndroidLocationPermissions(context)
            complete(
                when {
                    missing.isNotEmpty() -> VehicleLocationResult.PermissionRequired(missing)
                    fix != null && manager.isLocationEnabled -> VehicleLocationResult.Available(fix)
                    else -> VehicleLocationResult.Unavailable
                }
            )
        }
    } catch (_: SecurityException) {
        val missing = missingAndroidLocationPermissions(context)
        complete(if (missing.isNotEmpty()) VehicleLocationResult.PermissionRequired(missing) else VehicleLocationResult.Unavailable)
    }
    return AutoCloseable(cancellation::cancel)
}
