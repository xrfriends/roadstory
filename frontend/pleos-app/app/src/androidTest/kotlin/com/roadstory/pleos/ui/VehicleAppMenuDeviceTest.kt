package com.roadstory.pleos.ui

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roadstory.pleos.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Views/callbacks on a parked AAOS emulator, including embedded displays. */
@RunWith(AndroidJUnit4::class)
class VehicleAppMenuDeviceTest {
    @Test
    fun titleOpensMenuAndEachDestinationIsReachable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val destinations = listOf(
                    "지금 듣기" to "SCR-PLAYER-001",
                    "홈" to "SCR-HOME-001",
                    "내 이야기" to "SCR-SAVED-001",
                    "설정" to "SCR-SETTINGS-001",
                )
                destinations.forEach { (label, screenId) ->
                    activity.views().firstOrNull { it.contentDescription == "뒤로" }?.performClick()
                    val anchor = activity.views().first { it.contentDescription?.startsWith("앱 메뉴 ·") == true }
                    assertTrue("Park the emulator before running menu tests", anchor.isEnabled)
                    assertTrue(anchor.performClick())
                    val popup = checkNotNull(activity.appMenu)
                    assertTrue(popup.isShowing)
                    assertEquals("펼쳐짐", anchor.stateDescription)
                    val buttons = popup.contentView.descendants().filter { it.contentDescription?.toString() in listOf("홈", "지금 듣기", "내 이야기", "설정") }.toList()
                    assertEquals(listOf("홈", "지금 듣기", "내 이야기", "설정"), buttons.map { it.contentDescription.toString() })
                    assertEquals(1, buttons.count { it.isSelected })
                    buttons.single { it.contentDescription.toString() == label }.performClick()
                    assertFalse(popup.isShowing)
                    assertEquals("접힘", anchor.stateDescription)
                    assertTrue(activity.views().any { it.contentDescription == screenId })
                }
            }
        }
    }

    @Test
    fun backAndOutsideDismissMenuWithoutLeavingHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val anchor = activity.views().first { it.contentDescription == "앱 메뉴 · 홈" }
                assertTrue("Park the emulator before running menu tests", anchor.isEnabled)
                anchor.performClick()
            }
            // A real Back event arrives after Android attaches the popup's decor/input dispatcher.
            scenario.onActivity { activity ->
                val popup = checkNotNull(activity.appMenu)
                assertTrue(popup.isShowing)
                val decor = popup.contentView.rootView
                decor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                decor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                assertFalse("Back should dismiss the popup", popup.isShowing)
                assertTrue(activity.views().any { it.contentDescription == "SCR-HOME-001" })
                activity.views().first { it.contentDescription == "앱 메뉴 · 홈" }.performClick()
            }
            scenario.onActivity { activity ->
                val reopened = checkNotNull(activity.appMenu)
                assertTrue(reopened.isShowing)
                val outside = MotionEvent.obtain(0, 0, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
                try {
                    reopened.contentView.rootView.dispatchTouchEvent(outside)
                } finally {
                    outside.recycle()
                }
                assertFalse("Outside touch should dismiss the popup", reopened.isShowing)
                assertTrue(activity.views().any { it.contentDescription == "SCR-HOME-001" })
            }
        }
    }

    private fun MainActivity.views(): Sequence<View> = window.decorView.descendants()

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }
}
