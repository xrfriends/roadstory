package com.roadstory.pleos.ui

import com.roadstory.pleos.platform.DrivingRestriction

data class VehicleAppMenuEntry(val label: String, val screen: VehicleScreen)

/** Product navigation, independent of the Android popup and PLEOS SDK. */
object VehicleAppMenu {
    val entries = listOf(
        VehicleAppMenuEntry("홈", VehicleScreen.HOME),
        VehicleAppMenuEntry("지금 듣기", VehicleScreen.PLAYER),
        VehicleAppMenuEntry("내 이야기", VehicleScreen.COLLECTION),
        VehicleAppMenuEntry("설정", VehicleScreen.SETTINGS),
    )

    fun availableEntries(restriction: DrivingRestriction): List<VehicleAppMenuEntry> =
        if (restriction.blocksDetailedContent) emptyList() else entries
}
