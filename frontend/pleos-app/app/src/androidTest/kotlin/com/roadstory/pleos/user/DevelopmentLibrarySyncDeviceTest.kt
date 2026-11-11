package com.roadstory.pleos.user

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DevelopmentLibrarySyncDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun guestAndTwoServerAccountsKeepSeparateLibraries() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyDevelopmentLibrary") == "true")
        val credentialFile = File(context.filesDir, "library-sync-test-credentials")
        val credentials = try {
            check(credentialFile.isFile) { "Test credentials must be staged in app-private storage." }
            credentialFile.readLines().filter { it.isNotBlank() }
        } finally {
            credentialFile.delete()
        }
        assertEquals("Two dedicated development accounts are required", 2, credentials.size)
        val sync = VehicleLibrarySync.get(context)
        val store = VehicleUserStore(context)
        val guest = LibraryItem("guest-fixture-${UUID.randomUUID()}", "비회원 시험 이야기")
        val story = LibraryItem("story-published-1", "성수동 수제화 거리", "성수동", "INDUSTRY")
        val course = LibraryItem("course-seongsu", "성수 산업유산 드라이브")
        var addedGuestStory = false
        var addedStory = false
        var addedCourse = false
        assertTrue("Development sync must be enabled on this APK", sync.developmentSignInAvailable)

        try {
            instrumentation.runOnMainSync {
                sync.signOut()
                store.toggleStory(guest)
                if (!store.isStorySaved(story.id)) addedGuestStory = store.toggleStory(story)
                assertTrue(store.isStorySaved(guest.id))
                sync.signInForDevelopment(credentials[0])
            }
            await("first development account") { sync.status.accountId == "dev-account-a" }
            instrumentation.runOnMainSync {
                assertFalse(store.isStorySaved(guest.id))
                assertFalse("Guest bookmarks require an explicit import", store.isStorySaved(story.id))
                assertEquals(0, sync.importGuestBookmarks("dev-account-b", setOf(story.id), emptySet()))
                addedStory = sync.importGuestBookmarks("dev-account-a", setOf(story.id), emptySet()) == 1
                assertTrue(addedStory)
                if (!store.isCourseSaved(course.id)) addedCourse = store.toggleCourse(course)
                store.recordListening(story)
            }
            await("first account's saved items and listening history") {
                sync.status.mode == LibrarySyncMode.SYNCED && sync.status.pendingCount == 0 &&
                    store.isStorySaved(story.id) && store.isCourseSaved(course.id) && store.history.any { it.id == story.id }
            }

            instrumentation.runOnMainSync {
                sync.signOut()
                assertTrue(store.isStorySaved(guest.id))
                assertTrue(store.isStorySaved(story.id))
                sync.signInForDevelopment(credentials[1])
            }
            await("second development account") { sync.status.accountId == "dev-account-b" }
            instrumentation.runOnMainSync {
                assertFalse(store.isStorySaved(guest.id))
                assertFalse("The dedicated second account should not see account A's story", store.isStorySaved(story.id))
                assertFalse("The dedicated second account should not see account A's course", store.isCourseSaved(course.id))
                assertFalse("The dedicated second account should not see account A's history", store.history.any { it.id == story.id })
                sync.signOut()
                sync.signInForDevelopment(credentials[0])
            }
            await("first account's saved server data after signing back in") {
                sync.status.accountId == "dev-account-a" && sync.status.mode == LibrarySyncMode.SYNCED &&
                    store.isStorySaved(story.id) && store.isCourseSaved(course.id)
            }
            instrumentation.runOnMainSync {
                if (addedStory) store.toggleStory(story)
                if (addedCourse) store.toggleCourse(course)
            }
            await("test bookmark cleanup") { sync.status.mode == LibrarySyncMode.SYNCED && sync.status.pendingCount == 0 }
        } finally {
            instrumentation.runOnMainSync {
                sync.signOut()
                if (store.isStorySaved(guest.id)) store.toggleStory(guest)
                if (addedGuestStory && store.isStorySaved(story.id)) store.toggleStory(story)
            }
        }
    }

    private fun await(description: String, matches: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        do {
            var matched = false
            instrumentation.runOnMainSync { matched = matches() }
            if (matched) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Timed out waiting for $description")
    }
}
