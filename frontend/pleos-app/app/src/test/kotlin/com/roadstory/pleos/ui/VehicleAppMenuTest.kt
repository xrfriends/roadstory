package com.roadstory.pleos.ui

import com.roadstory.pleos.platform.DrivingRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleAppMenuTest {
    @Test
    fun `parked menu routes each label to its own screen`() {
        val entries = VehicleAppMenu.availableEntries(DrivingRestriction(false, 0))
        assertEquals(
            linkedMapOf(
                "홈" to VehicleScreen.HOME,
                "지금 듣기" to VehicleScreen.PLAYER,
                "내 이야기" to VehicleScreen.COLLECTION,
                "설정" to VehicleScreen.SETTINGS,
            ),
            entries.associate { it.label to it.screen },
        )
    }

    @Test
    fun `driving and unknown fail closed even without individual restriction flags`() {
        assertTrue(VehicleAppMenu.availableEntries(DrivingRestriction(true, 0)).isEmpty())
        assertTrue(VehicleAppMenu.availableEntries(DrivingRestriction(true, 255)).isEmpty())
        assertTrue(VehicleAppMenu.availableEntries(DrivingRestriction(false, 8)).isEmpty())
    }
}
