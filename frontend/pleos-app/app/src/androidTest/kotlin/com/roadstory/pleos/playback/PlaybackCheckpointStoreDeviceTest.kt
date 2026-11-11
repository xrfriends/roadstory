package com.roadstory.pleos.playback

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.user.ownerKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackCheckpointStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("road_story_playback_checkpoint_device_test", Context.MODE_PRIVATE)
    private val isolatedContext = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
    }

    @Before
    @After
    fun clearFixtures() {
        preferences.edit().clear().commit()
    }

    @Test
    fun guestAndVerifiedAccountsRestoreOnlyTheirOwnCheckpoints() {
        val guest = checkpoint("guest-story", sentenceIndex = 1, listenedMs = 3_000L)
        val first = checkpoint("account-story", sentenceIndex = 0, listenedMs = 4_294_967_296L)
            .copy(audioPositionMs = 12_360L, audioDurationMs = 78_100L)
        val second = checkpoint("account-story", sentenceIndex = 4, listenedMs = 7_000L)
        PlaybackCheckpointStore(isolatedContext).apply {
            save(null, guest)
            save("test-account-a", first)
            save("test-account-b", second)
        }

        val reopened = PlaybackCheckpointStore(isolatedContext)
        assertEquals(guest, reopened.load(null))
        assertEquals(first, reopened.load("test-account-a"))
        assertEquals(second, reopened.load("test-account-b"))
        assertNull(reopened.load("test-account-c"))

        reopened.save("test-account-a", null)
        assertNull(reopened.load("test-account-a"))
        assertEquals(guest, reopened.load(null))
        assertEquals(second, reopened.load("test-account-b"))
    }

    @Test
    fun malformedAccountRecordDoesNotAffectGuestPlayback() {
        val account = "test-account-a"
        val guest = checkpoint("guest-story", sentenceIndex = 1, listenedMs = 3_000L)
        val store = PlaybackCheckpointStore(isolatedContext)
        store.save(null, guest)
        val accountKey = "account.${ownerKey(account)}"
        preferences.edit().putString(accountKey, "invalid JSON").commit()
        assertNull(store.load(account))
        preferences.edit().putInt(accountKey, 42).commit()
        assertNull(store.load(account))
        assertEquals(guest, store.load(null))

        store.save(account, null)
        assertEquals(setOf("guest"), preferences.all.keys)
    }

    private fun checkpoint(id: String, sentenceIndex: Int, listenedMs: Long) = PlaybackCheckpoint(
        story = QueueStory(id, "테스트 이야기", "테스트 장소", 60, "HISTORY", true),
        narrationFingerprint = "a".repeat(64),
        sentenceIndex = sentenceIndex,
        listenedMs = listenedMs,
    )
}
