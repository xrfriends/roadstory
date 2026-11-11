package com.roadstory.pleos.ui

import com.roadstory.pleos.platform.DrivingRestriction

object VehicleUiPolicy {
    val primaryActions = listOf("주변 이야기 듣기", "목적지까지 이야기", "테마 드라이브", "최근 이어듣기")
    const val primaryActionMinHeightDp = 80
    const val modeActionMinHeightDp = 64
    const val maxDrivingTextTiers = 3
    private val drivingActions = setOf("toggle", "next", "save", "dislike", "stop")

    fun permittedScreen(requested: VehicleScreen, restriction: DrivingRestriction): VehicleScreen =
        if (restriction.blocksDetailedContent) VehicleScreen.DRIVE_LIMIT else requested

    fun isDrivingAction(action: String): Boolean = action in drivingActions
}
