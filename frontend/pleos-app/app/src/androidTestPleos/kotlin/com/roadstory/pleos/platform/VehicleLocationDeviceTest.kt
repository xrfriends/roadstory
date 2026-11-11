package com.roadstory.pleos.platform

import ai.pleos.playground.location.fused.FusedLocation
import android.content.pm.PackageManager
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class VehicleLocationDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun requiresPleosPermissionWhenSupportedAndUngranted() {
        assumeTrue(FusedLocation(context).isSupported())
        assumeTrue(context.checkSelfPermission(pleosPermission) != PackageManager.PERMISSION_GRANTED)
        val source = VehicleLocationSourceFactory.create(context)
        assertEquals(listOf(pleosPermission), source.missingPermissions())
        assertEquals(
            VehicleLocationResult.PermissionRequired(listOf(pleosPermission)),
            read(source),
        )
    }

    @Test fun readsFreshEmulatorLocationFromPleosService() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyPleosLocation") == "true")
        assertTrue("PLEOS FusedLocation is unavailable on this device", FusedLocation(context).isSupported())
        val source = VehicleLocationSourceFactory.create(context)
        assertTrue("Grant the PLEOS location permission on the emulator before this check", source.missingPermissions().isEmpty())

        val available = read(source) as? VehicleLocationResult.Available
            ?: throw AssertionError("The PLEOS service did not provide a usable fresh location")
        // The documented emulator console feeds this public test point while the request runs.
        assertTrue(
            "Location did not match the injected emulator fixture",
            abs(available.fix.latitude - 37.544) < 0.002 && abs(available.fix.longitude - 127.056) < 0.002,
        )
    }

    private fun read(source: VehicleLocationSource): VehicleLocationResult {
        val delivered = CountDownLatch(1)
        var onMain = false
        var result: VehicleLocationResult? = null
        source.request {
            onMain = Looper.myLooper() == Looper.getMainLooper()
            result = it
            delivered.countDown()
        }.use {
            assertTrue("Location request did not finish within the timeout", delivered.await(12, TimeUnit.SECONDS))
        }
        assertTrue("Location callback did not arrive on the main thread", onMain)
        return checkNotNull(result)
    }

    private companion object {
        const val pleosPermission = "pleos.car.permission.FUSED_LOCATION"
    }
}
