package com.roadstory.pleos.playback

import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.user.ListeningFrequency
import com.roadstory.pleos.user.VehicleUserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** TS-PLAY-013/014/016/017. Requires a parked PLEOS emulator and the local Story API. */
@RunWith(AndroidJUnit4::class)
class PlaybackQueueDeviceTest {
    @Test fun manualNextChangesStoryWithAutoplayOffAndDoesNotWrap() = withSession { session, store ->
        store.autoplay = false
        session.command { sendCustomAction(PlaybackSessionContract.START_NEARBY, null) }
        val first = session.await("first story playing") { it.isPlaying(FIRST) }
        val extras = checkNotNull(first.extras)
        assertEquals("두 번째 데모 이야기", extras.getString(PlaybackSessionContract.NEXT_TITLE))
        assertEquals(12, extras.getInt(PlaybackSessionContract.NEXT_DURATION_SECONDS))
        assertFalse(extras.getBoolean(PlaybackSessionContract.AUTOPLAY_ENABLED))

        session.command { skipToNext() }
        val second = session.await("different story playing") { it.isPlaying(SECOND) }
        assertEquals(0L, second.actions and PlaybackState.ACTION_SKIP_TO_NEXT)
        assertTrue(store.history.any { it.id == SECOND })
        session.command { skipToNext() }
        session.await("second story completed at the queue tail") { it.state == PlaybackState.STATE_PAUSED && it.storyId() == SECOND }
    }

    @Test fun finishAfterCurrentDoesNotInterruptAndNewSessionAutoplays() = withSession { session, store ->
        store.autoplay = true
        session.command { sendCustomAction(PlaybackSessionContract.START_NEARBY, null) }
        session.await("first story playing") { it.isPlaying(FIRST) }
        session.command { sendCustomAction(PlaybackSessionContract.FINISH_AFTER_CURRENT, null) }
        session.await("finish scheduled without interrupting the current story") { it.isPlaying(FIRST) && it.finishing() }
        session.await("first story completed without auto advance", 65) {
            it.state == PlaybackState.STATE_PAUSED && it.storyId() == FIRST && it.finishing()
        }

        session.command { sendCustomAction(PlaybackSessionContract.START_NEARBY, null) }
        session.await("new session clears the finish request") { it.isPlaying(FIRST) && !it.finishing() }
        session.await("natural completion advances to the next story", 65) { it.isPlaying(SECOND) }
    }

    @Test fun familyModePromotesAnExplicitlySuitableStory() = withSession { session, store ->
        store.autoplay = false
        store.setFrequency(industry, ListeningFrequency.OFTEN)
        store.familyMode = true
        session.startNearby()
        val playing = session.await("family candidate first") { it.isPlaying(SECOND) }
        assertEquals("성수동 수제화 거리", playing.extras?.getString(PlaybackSessionContract.NEXT_TITLE))

        store.familyMode = false
        session.startNearby()
        session.await("frequent topic first without family mode") { it.isPlaying(FIRST) }
    }

    @Test fun liveFilteringUpdatesNextAndStillAllowsExplicitReplay() = withSession { session, store ->
        store.autoplay = false
        session.startNearby()
        session.await("first story with a next candidate") { it.isPlaying(FIRST) && it.canSkip() }

        store.setFrequency(dailyLife, ListeningFrequency.OFF)
        session.await("disabled topic removed while first story plays") { it.isPlaying(FIRST) && !it.canSkip() }
        store.setFrequency(dailyLife, ListeningFrequency.SOMETIMES)
        session.await("re-enabled topic restored while first story plays") { it.isPlaying(FIRST) && it.canSkip() }

        session.command { skipToNext() }
        session.await("second story playing before dislike") { it.isPlaying(SECOND) }
        val dislikedStory = store.currentStory
        assertEquals(SECOND, dislikedStory.id)
        assertEquals("DAILY_LIFE", dislikedStory.category)
        store.setStoryDisliked(dislikedStory, disliked = true)
        store.setFrequency(industry, ListeningFrequency.OFF)
        session.startNearby()
        val empty = session.await("all candidates excluded by preferences") {
            it.state == PlaybackState.STATE_STOPPED &&
                it.extras?.getBoolean(PlaybackSessionContract.FILTERED_BY_PREFERENCES) == true
        }
        assertTrue(empty.extras?.getBoolean(PlaybackSessionContract.QUEUE_EMPTY) == true)
        assertFalse(empty.canSkip())

        session.command { playFromMediaId(SECOND, null) }
        session.await("explicit selection can still replay a disliked story") { it.isPlaying(SECOND) }
    }

    private fun withSession(test: (DeviceSession, VehicleUserStore) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = VehicleUserStore(context)
        val initialAutoplay = store.autoplay
        val initialStory = store.currentStory
        val initialFamilyMode = store.familyMode
        val initialFrequencies = listOf(industry, dailyLife).associateWith(store::frequency)
        val initialDislikes = listOf(FIRST, SECOND).associateWith {
            store.isStoryDisliked(it) to store.dislikedStoryCategory(it)
        }
        try {
            store.familyMode = false
            initialFrequencies.keys.forEach { store.setFrequency(it, ListeningFrequency.SOMETIMES) }
            initialDislikes.keys.forEach { store.setStoryDisliked(it, false) }
            DeviceSession().use { session ->
                session.connect()
                test(session, store)
            }
        } finally {
            store.autoplay = initialAutoplay
            store.currentStory = initialStory
            store.familyMode = initialFamilyMode
            initialFrequencies.forEach(store::setFrequency)
            initialDislikes.forEach { (id, preference) ->
                store.setStoryDisliked(id, preference.first, preference.second)
            }
        }
    }

    private class DeviceSession : AutoCloseable {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context = instrumentation.targetContext
        private val connected = CountDownLatch(1)
        private val updates = LinkedBlockingQueue<PlaybackState>()
        private var browser: MediaBrowser? = null
        private var controller: MediaController? = null
        private val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                state?.let(updates::offer)
            }
        }

        fun connect() {
            instrumentation.runOnMainSync {
                browser = MediaBrowser(context, ComponentName(context, RoadStoryPlaybackService::class.java),
                    object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() {
                            controller = MediaController(context, checkNotNull(browser).sessionToken).apply {
                                registerCallback(callback)
                            }
                            connected.countDown()
                        }
                    }, null).also { it.connect() }
            }
            assertTrue("MediaBrowser connection failed", connected.await(10, TimeUnit.SECONDS))
        }

        fun command(action: MediaController.TransportControls.() -> Unit) {
            instrumentation.runOnMainSync { checkNotNull(controller).transportControls.action() }
        }

        fun startNearby() = command { sendCustomAction(PlaybackSessionContract.START_NEARBY, null) }

        fun await(description: String, timeoutSeconds: Long = 25, condition: (PlaybackState) -> Boolean): PlaybackState {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (true) {
                val state = updates.poll((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
                    ?: throw AssertionError("Timed out waiting for $description")
                if (state.state == PlaybackState.STATE_ERROR) {
                    throw AssertionError("$description failed: ${state.errorMessage}")
                }
                if (condition(state)) return state
            }
        }

        override fun close() {
            instrumentation.runOnMainSync {
                controller?.transportControls?.stop()
                controller?.unregisterCallback(callback)
                browser?.disconnect()
            }
        }
    }

    private fun PlaybackState.storyId() = extras?.getString(PlaybackSessionContract.CURRENT_ID)
    private fun PlaybackState.isPlaying(id: String) = state == PlaybackState.STATE_PLAYING && storyId() == id
    private fun PlaybackState.canSkip() = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
    private fun PlaybackState.finishing() = extras?.getBoolean(PlaybackSessionContract.END_AFTER_CURRENT) == true

    private companion object {
        const val FIRST = "story-published-1"
        const val SECOND = "story-published-2"
        val industry = VehicleUserStore.topics.single { it.key == "INDUSTRY" }
        val dailyLife = VehicleUserStore.topics.single { it.key == "DAILY_LIFE" }
    }
}
