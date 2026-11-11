package com.roadstory.pleos.ui

import com.roadstory.pleos.platform.DrivingRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleUiPolicyTest {
    @Test
    fun `SCR-HOME-001 Primary Action은 최대 4개다`() {
        assertEquals(4, VehicleUiPolicy.primaryActions.size)
    }

    @Test
    fun `Vehicle Design Token은 큰 Touch Target과 3단계 Text Hierarchy를 유지한다`() {
        assertEquals(80, VehicleUiPolicy.primaryActionMinHeightDp)
        assertEquals(64, VehicleUiPolicy.modeActionMinHeightDp)
        assertEquals(3, VehicleUiPolicy.maxDrivingTextTiers)
    }

    @Test
    fun `주행 초기화 실패 및 모순된 제한 플래그는 모든 화면을 제한한다`() {
        listOf(DrivingRestriction.RESTRICTED, DrivingRestriction(true, 511), DrivingRestriction(false, 8)).forEach { restriction ->
            VehicleScreen.entries.forEach { screen ->
                assertEquals("$screen under $restriction", VehicleScreen.DRIVE_LIMIT, VehicleUiPolicy.permittedScreen(screen, restriction))
            }
        }
    }

    @Test
    fun `제한이 해제되면 기존 화면을 복원한다`() {
        VehicleScreen.entries.forEach { screen ->
            assertEquals(screen, VehicleUiPolicy.permittedScreen(screen, DrivingRestriction(false, 0)))
        }
    }

    @Test
    fun `주행 명령은 다섯 재생 조작만 허용한다`() {
        listOf("toggle", "next", "save", "dislike", "stop").forEach { assertTrue(VehicleUiPolicy.isDrivingAction(it)) }
        listOf("play", "play:story-published-1", "save-course", "previous", "seek", "").forEach {
            assertFalse(VehicleUiPolicy.isDrivingAction(it))
        }
    }
}
