package com.roadstory.pleos.playback

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.MainActivity
import com.roadstory.pleos.user.ListeningFrequency
import com.roadstory.pleos.user.VehicleUserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the local public catalog and actual PLEOS narration. No location permission is needed. */
@RunWith(AndroidJUnit4::class)
class JourneyCourseDeviceTest {
    @Test
    fun publishedCourseOpensTheMatchingStopAndPlaysItsStory() {
        val store = VehicleUserStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val priorStory = store.currentStory
        val industry = VehicleUserStore.topics.single { it.key == "INDUSTRY" }
        val priorFrequency = store.frequency(industry)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue(await(scenario) { activity ->
                    activity.views().firstOrNull { it.tag == "focus:course:course-seongsu:open" }?.performClick() == true
                })
                assertTrue(await(scenario) { activity ->
                    activity.hasText("성수 산업유산 드라이브") &&
                        activity.hasText("1. 성수역") &&
                        activity.hasText("성수동 수제화 거리") &&
                        activity.views().any { it.tag == "focus:course:course-seongsu:guide" }
                })
                // An explicit row selection is valid even when disabled for automatic recommendations.
                scenario.onActivity { store.setFrequency(industry, ListeningFrequency.OFF) }
                assertTrue(await(scenario) { activity ->
                    activity.views().firstOrNull { it.contentDescription == "성수동 수제화 거리 듣기" }?.performClick() == true
                })
                assertTrue(await(scenario, 25_000) { activity ->
                    activity.hasText("지금 듣는 이야기") && activity.hasText("성수동 수제화 거리")
                })
                assertEquals(FIRST, store.currentStory.id)
                scenario.onActivity { activity ->
                    activity.views().first { it.tag == "focus:player:stop" }.performClick()
                }
            }
        } finally {
            store.currentStory = priorStory
            store.setFrequency(industry, priorFrequency)
        }
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 12_000,
        condition: (MainActivity) -> Boolean,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return true
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    private fun MainActivity.hasText(text: String) = views().filterIsInstance<TextView>().any { it.text.toString() == text }
    private fun MainActivity.views() = window.decorView.descendants()
    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
    }

    private companion object {
        const val FIRST = "story-published-1"
    }
}
