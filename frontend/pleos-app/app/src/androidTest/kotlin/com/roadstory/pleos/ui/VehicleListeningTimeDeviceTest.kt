package com.roadstory.pleos.ui

import android.app.Dialog
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roadstory.pleos.MainActivity
import com.roadstory.pleos.platform.DrivingRestriction
import com.roadstory.pleos.user.VehicleUserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VehicleListeningTimeDeviceTest {
    @Test
    fun pausedPlayerKeepsAccumulatedTimeAndShowsSentenceResume() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val store = VehicleUserStore(activity)
                val renderer = RoadStoryScreens(
                    context = activity,
                    navigate = {},
                    playbackAction = {},
                    dislikeStory = {},
                    openAppMenu = {},
                    userStore = store,
                    refresh = {},
                    canUseParkedControls = { true },
                )
                val parked = DrivingRestriction(false, 0)
                val paused = PlaybackUiState(
                    currentStoryId = store.currentStory.id,
                    currentDurationSeconds = 90,
                    listenedMs = 72_500L,
                    listeningSinceElapsedMs = SystemClock.elapsedRealtime() - 5_000L,
                    resumeAtSentence = true,
                )
                val player = renderer.render(VehicleScreen.PLAYER, parked, expanded = false, playback = paused)
                assertEquals("들은 시간 1:12\n예상 남은 시간 0:18", player.listeningTimeText())
                assertTrue(player.descendants().filterIsInstance<TextView>().any {
                    it.text.toString() == "이어듣기는 멈춘 문장부터 시작합니다."
                })

                val exceededEstimate = renderer.render(
                    VehicleScreen.PLAYER, parked, expanded = false, playback = paused.copy(listenedMs = 91_000L),
                )
                assertEquals("들은 시간 1:31", exceededEstimate.listeningTimeText())

                val completed = renderer.render(
                    VehicleScreen.PLAYER, parked, expanded = false, playback = paused.copy(resumeAtSentence = false),
                )
                assertEquals("들은 시간 1:12", completed.listeningTimeText())

                val differentStory = renderer.render(
                    VehicleScreen.PLAYER, parked, expanded = false, playback = paused.copy(currentStoryId = "previous-story"),
                )
                assertNull(differentStory.findViewWithTag<TextView>("playback:listening-time"))
            }
        }
    }

    @Test
    fun elapsedLabelUpdatesWithoutMovingKeyboardFocus() {
        val updated = CountDownLatch(1)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var dialog: Dialog? = null
            lateinit var control: View
            try {
                scenario.onActivity { activity ->
                    val elapsed = activity.listeningTime(
                        PlaybackUiState(
                            playing = true,
                            currentDurationSeconds = 90,
                            listeningSinceElapsedMs = SystemClock.elapsedRealtime(),
                        ),
                        22f,
                    )
                    val firstText = elapsed.text.toString()
                    elapsed.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                            if (text.toString() != firstText) updated.countDown()
                        }
                        override fun afterTextChanged(text: Editable?) = Unit
                    })
                    control = activity.action("일시정지") {}.apply { isFocusableInTouchMode = true }
                    dialog = Dialog(activity).apply {
                        setContentView(LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(elapsed)
                            addView(control)
                        })
                        show()
                    }
                }
                scenario.onActivity { assertTrue(control.requestFocus()) }
                assertTrue("The listening time did not advance", updated.await(5, TimeUnit.SECONDS))
                scenario.onActivity { assertSame(control, dialog?.currentFocus) }
            } finally {
                scenario.onActivity { dialog?.dismiss() }
            }
        }
    }

    private fun View.listeningTimeText() = checkNotNull(findViewWithTag<TextView>("playback:listening-time")).text.toString()

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }
}
