package com.roadstory.pleos.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleLocationQualityTest {
    @Test fun androidFixRequiresFreshAccurateCoordinates() {
        assertEquals(
            VehicleLocationFix(37.0, 127.0, 90.0, 5.0),
            VehicleLocationQuality.fromAndroid(37.0, 127.0, 5_000, 25f, 90.0, 5.0),
        )
        assertNull(VehicleLocationQuality.fromAndroid(37.0, 127.0, 30_001, 25f, null, null))
        assertNull(VehicleLocationQuality.fromAndroid(37.0, 127.0, -1, 25f, null, null))
        assertNull(VehicleLocationQuality.fromAndroid(37.0, 127.0, 5_000, null, null, null))
        assertNull(VehicleLocationQuality.fromAndroid(37.0, 127.0, 5_000, 101f, null, null))
        assertNull(VehicleLocationQuality.fromAndroid(37.0, Double.NaN, 5_000, 25f, null, null))
    }

    @Test fun unknownHeadingAndSpeedDoNotBecomeMeasuredValues() {
        assertEquals(
            VehicleLocationFix(37.0, 127.0),
            VehicleLocationQuality.fromAndroid(37.0, 127.0, 5_000, 25f, -1.0, Double.NaN),
        )
        assertEquals(
            VehicleLocationFix(37.0, 127.0, heading = 180.0),
            VehicleLocationQuality.fromPleos(37.0, 127.0, 5_000, 0.8f, 180.0),
        )
    }

    @Test fun pleosRejectsStaleAndLowConfidenceFixes() {
        assertNull(VehicleLocationQuality.fromPleos(37.0, 127.0, 5_000, 0.4f, null))
        assertNull(VehicleLocationQuality.fromPleos(37.0, 127.0, 5_000, Float.NaN, null))
        assertNull(VehicleLocationQuality.fromPleos(37.0, 127.0, 30_001, 0.8f, null))
        assertNull(VehicleLocationQuality.fromPleos(91.0, 127.0, 5_000, 0.8f, null))
    }
}
