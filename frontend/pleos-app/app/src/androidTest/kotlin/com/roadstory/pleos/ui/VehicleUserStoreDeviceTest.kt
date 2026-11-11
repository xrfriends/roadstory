package com.roadstory.pleos.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.user.LibraryItem
import com.roadstory.pleos.user.ListeningFrequency
import com.roadstory.pleos.user.VehicleUserStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleUserStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "road_story_user_device_test"
    private fun store() = VehicleUserStore(context, namespace)

    @Before fun clearTestState() {
        context.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun preferencesSurviveNewStoreInstances() {
        val store = store()
        val topic = VehicleUserStore.topics.first()
        store.setFrequency(topic, ListeningFrequency.OFF)
        store.autoplay = false
        store.familyMode = true
        val reopened = store()
        assertEquals(ListeningFrequency.OFF, reopened.frequency(topic))
        assertFalse(reopened.autoplay)
        assertTrue(reopened.familyMode)
    }

    @Test fun savedStoriesAndCoursesStaySeparateAndCanBeRemoved() {
        val item = LibraryItem("test-item", "기기 테스트 항목")
        val store = store()
        assertTrue(store.toggleStory(item))
        assertEquals(listOf(item), store().savedStories)
        assertTrue(store.savedCourses.isEmpty())
        assertTrue(store.toggleCourse(item))
        assertFalse(store.toggleStory(item))
        assertTrue(store().savedStories.isEmpty())
        assertEquals(listOf(item), store().savedCourses)
    }

    @Test fun listeningHistoryDeduplicatesByStoryAndUpdatesMetadata() {
        val store = store()
        val first = LibraryItem("story-one", "첫 번째")
        val second = LibraryItem("story-two", "두 번째")
        store.recordListening(first)
        store.recordListening(second)
        val updated = first.copy(title = "갱신된 첫 번째", category = "INDUSTRY")
        store.recordListening(updated)
        assertEquals(listOf(updated, second), store().history)
        assertEquals(updated, store().currentStory)
    }

    @Test fun dislikeKeepsLegacyIdsAndPersistsCategoryForEachStory() {
        val legacy = "legacy-id"
        context.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit()
            .putStringSet("dislikedStoryIds", setOf(legacy)).commit()
        val store = store()
        assertTrue(store.isStoryDisliked(legacy))
        assertTrue(store.dislikedCategories.isEmpty())

        val story = LibraryItem(legacy, "기존 이야기", category = "HISTORY")
        store.setStoryDisliked(story, true)
        store.setStoryDisliked(story, true)
        store.setStoryDisliked("other-id", true, "HISTORY")

        val reopened = store()
        assertEquals(setOf(legacy, "other-id"), reopened.dislikedStoryIds)
        assertEquals(setOf("HISTORY"), reopened.dislikedCategories)
        assertEquals("HISTORY", reopened.dislikedStoryCategory(legacy))
        reopened.setStoryDisliked(legacy, false)
        assertNull(reopened.dislikedStoryCategory(legacy))
        assertEquals(setOf("HISTORY"), reopened.dislikedCategories)
        reopened.setStoryDisliked("other-id", false)
        assertTrue(reopened.dislikedCategories.isEmpty())
    }
}
