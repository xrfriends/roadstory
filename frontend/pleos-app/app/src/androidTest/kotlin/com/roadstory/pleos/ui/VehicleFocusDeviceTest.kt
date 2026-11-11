package com.roadstory.pleos.ui

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

@RunWith(AndroidJUnit4::class)
class VehicleFocusDeviceTest {
    @Test fun savingKeepsKeyboardFocusWhenAccessibleLabelChanges() {
        val store = VehicleUserStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val story = store.currentStory
        val wasSaved = store.isStorySaved(story.id)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    repeat(2) {
                        val save = activity.window.decorView.descendants().first { it.tag == "focus:mini-player:save" }
                        // A preceding touchscreen interaction can leave the window in touch mode.
                        // Enter keyboard focus mode before checking focus across the redraw.
                        assertTrue("Save control did not enter keyboard focus mode", save.requestFocusFromTouch())
                        val oldLabel = save.contentDescription
                        save.performClick()
                        val focused = activity.window.decorView.findFocus()
                        assertEquals("focus:mini-player:save", focused?.tag)
                        assertNotEquals(oldLabel, focused?.contentDescription)
                    }
                    assertEquals(wasSaved, store.isStorySaved(story.id))
                }
            }
        } finally {
            if (store.isStorySaved(story.id) != wasSaved) store.toggleStory(story)
        }
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
    }
}
