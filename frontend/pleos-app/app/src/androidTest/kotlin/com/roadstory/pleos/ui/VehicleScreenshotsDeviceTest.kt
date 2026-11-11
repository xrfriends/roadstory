package com.roadstory.pleos.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.MainActivity
import com.roadstory.pleos.user.VehicleUserStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native rendered-frame artifacts for visual review; never substitutes these for design assets. */
@RunWith(AndroidJUnit4::class)
class VehicleScreenshotsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun captureMenuSettingsAndCollectionFrames() {
        val store = VehicleUserStore(instrumentation.targetContext)
        val story = store.currentStory
        val wasSaved = store.isStorySaved(story.id)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.views().first { it.contentDescription == "설정" }.performClick()
                }
                captureWindow(scenario, "visual-settings.png")
                scenario.onActivity { activity ->
                    activity.views().first { it.contentDescription == "앱 메뉴 · 설정" }.performClick()
                }
                instrumentation.waitForIdleSync()
                val displayFrame = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                save(displayFrame, "visual-menu-display.png")
                scenario.onActivity { activity ->
                    val popup = checkNotNull(activity.appMenu)
                    popup.contentView.descendants().first { it.contentDescription == "내 이야기" }.performClick()
                }
                captureWindow(scenario, "visual-collection.png")
                if (!wasSaved) store.toggleStory(story)
                captureWindow(scenario, "visual-collection-saved.png")
            }
        } finally {
            if (store.isStorySaved(story.id) != wasSaved) store.toggleStory(story)
        }
    }

    private fun captureWindow(scenario: ActivityScenario<MainActivity>, filename: String) {
        instrumentation.waitForIdleSync()
        val done = CountDownLatch(1)
        var result = -1
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(activity.window, bitmap, { status ->
                result = status
                if (status == PixelCopy.SUCCESS) save(bitmap, filename) else bitmap.recycle()
                done.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue("Timed out capturing $filename", done.await(10, TimeUnit.SECONDS))
        assertEquals("Native frame capture failed: $filename", PixelCopy.SUCCESS, result)
    }

    private fun save(bitmap: Bitmap, filename: String) {
        File(instrumentation.targetContext.filesDir, filename).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun MainActivity.views() = window.decorView.descendants()
    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
    }
}
