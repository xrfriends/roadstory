package com.roadstory.pleos.ui

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.MainActivity
import com.roadstory.pleos.user.ListeningFrequency
import com.roadstory.pleos.user.VehicleUserStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehiclePreferencesDeviceTest {
    @Test fun settingsControlsPersistAcrossActivityRecreation() {
        val store = VehicleUserStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val topic = VehicleUserStore.topics.first()
        val originalFrequency = store.frequency(topic)
        val originalAutoplay = store.autoplay
        val originalFamilyMode = store.familyMode
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.views().first { it.contentDescription == "설정" }.performClick()
                    assertTrue(activity.views().any { it.contentDescription == "SCR-SETTINGS-001" })
                    activity.views().first { it.contentDescription == "지명 유래 · 끄기" }.performClick()
                    activity.views().first { it.contentDescription == "이야기 자동 재생" }.performClick()
                    activity.views().first { it.contentDescription == "가족 · 어린이 우선" }.performClick()
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertTrue(activity.views().first { it.contentDescription == "지명 유래 · 끄기" }.isSelected)
                    assertEquals(!originalAutoplay, (activity.views().first { it.contentDescription == "이야기 자동 재생" } as PreferenceSwitch).isChecked)
                    assertEquals(!originalFamilyMode, (activity.views().first { it.contentDescription == "가족 · 어린이 우선" } as PreferenceSwitch).isChecked)
                    activity.views().first { it.contentDescription == "관심 주제 다음 페이지" }.performClick()
                    assertTrue(activity.views().any { it.contentDescription == "인물 · 자주" })
                    activity.views().first { it.contentDescription == "관심 주제 다음 페이지" }.performClick()
                    assertTrue(activity.views().any { it.contentDescription == "자연과 지형 · 자주" })
                    assertFalse(activity.views().first { it.contentDescription == "관심 주제 다음 페이지" }.isEnabled)
                }
            }
            assertEquals(ListeningFrequency.OFF, store.frequency(topic))
        } finally {
            store.setFrequency(topic, originalFrequency)
            store.autoplay = originalAutoplay
            store.familyMode = originalFamilyMode
        }
    }

    @Test fun libraryTabsUseActualVehicleStoreCounts() {
        val store = VehicleUserStore(InstrumentationRegistry.getInstrumentation().targetContext)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.views().first { it.contentDescription?.startsWith("앱 메뉴 ·") == true }.performClick()
                checkNotNull(activity.appMenu).contentView.descendants().first { it.contentDescription == "내 이야기" }.performClick()
                assertTrue(activity.views().any { it.contentDescription == "SCR-SAVED-001" })
                listOf("저장 탭", "코스 탭", "청취 기록 탭").forEachIndexed { index, label ->
                    activity.views().first { it.contentDescription == label }.performClick()
                    assertTrue(activity.views().first { it.contentDescription == label }.isSelected)
                    val counts = listOf(store.savedStories.size, store.savedCourses.size, store.history.size)
                    val names = listOf("저장", "코스", "청취 기록")
                    val button = activity.views().first { it.contentDescription == label } as android.widget.Button
                    assertEquals("${names[index]} ${counts[index]}", button.text.toString())
                }
            }
        }
    }

    private fun MainActivity.views() = window.decorView.descendants()
    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
    }
}
