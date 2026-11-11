package com.roadstory.pleos.playback

import android.content.ComponentName
import android.content.Context
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.MainActivity
import com.roadstory.pleos.user.LibraryItem
import com.roadstory.pleos.user.VEHICLE_GUEST_NAMESPACE
import com.roadstory.pleos.user.VehicleLibrarySync
import com.roadstory.pleos.user.VehicleUserStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Run the seed with the fixture API online, then force-stop the app and shut down the API before verifying. */
@RunWith(AndroidJUnit4::class)
class OfflineAudioPlaybackDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val checkpointStore = PlaybackCheckpointStore(context)
    private val audioCache = StoryAudioCache(context)
    private val guestPreferences = context.getSharedPreferences(VEHICLE_GUEST_NAMESPACE, Context.MODE_PRIVATE)
    private val savedGuestMetadata = File(context.filesDir, "offline-audio-test-guest")
    private val seed = File(context.filesDir, "offline-audio-test-seed")

    @Test
    fun seedOnlinePosition() {
        requireFixture()
        seed.delete()
        instrumentation.runOnMainSync { VehicleLibrarySync.get(context).signOut() }
        preserveGuestState()
        try {
            DeviceSession().use { session ->
                session.connect()
                try {
                    session.command { playFromMediaId(STORY_ID, null) }
                    val playing = session.awaitState(PlaybackState.STATE_PLAYING)
                    assertEquals(STORY_ID, playing.storyId)
                    assertFalse("The seed requires the fixture API online", playing.offlineAudio)
                    SystemClock.sleep(3_500)
                    session.command { pause() }
                    val paused = session.awaitState(PlaybackState.STATE_PAUSED)
                    val position = paused.position
                    assertTrue("Expected the paused media position to advance, got $position ms", position >= 750L)
                    assertEquals(STORY_ID, paused.storyId)
                    assertEquals(position, checkNotNull(checkpointStore.load(null)?.audioPositionMs))
                    val localAudio = checkNotNull(audioCache.find(STORY_ID)) { "No verified local file was downloaded" }
                    assertTrue(position < localAudio.asset.durationMs)
                    seed.writeText(JSONObject()
                        .put("process", Process.myPid())
                        .put("positionMs", position)
                        .put("audioSha256", localAudio.asset.sha256)
                        .toString())
                } catch (failure: Throwable) {
                    runCatching(session::stop)
                    throw failure
                }
            }
        } finally {
            if (!seed.isFile) restoreGuestState()
        }
    }

    @Test
    fun resumeOfflineAfterRestart() {
        requireFixture()
        check(seed.isFile) { "Run seedOnlinePosition with the fixture API online first" }
        try {
            val saved = JSONObject(seed.readText())
            assertNotEquals("Verify in a new app process", saved.getInt("process"), Process.myPid())
            val savedPosition = saved.getLong("positionMs")
            assertFixtureApiOffline()
            assertEquals(saved.getString("audioSha256"), audioCache.find(STORY_ID)?.asset?.sha256)

            DeviceSession().use { session ->
                session.connect()
                try {
                    val restored = session.awaitState(PlaybackState.STATE_PAUSED, waitingForPlay = true)
                    assertEquals(STORY_ID, restored.storyId)
                    assertEquals(savedPosition, restored.position)
                    session.assertQuiet()

                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        session.pressPlay(scenario)
                        val resumed = session.awaitState(PlaybackState.STATE_PLAYING)
                        assertEquals(STORY_ID, resumed.storyId)
                        assertTrue("Playback did not use the downloaded audio", resumed.offlineAudio)
                        assertTrue("Saved $savedPosition ms and resumed at ${resumed.position} ms",
                            abs(resumed.position - savedPosition) <= 300L)
                        SystemClock.sleep(1_000L)
                        session.command { pause() }
                        val paused = session.awaitState(PlaybackState.STATE_PAUSED)
                        assertTrue("Offline playback position did not advance", paused.position > resumed.position)
                    }
                    session.stop()
                } catch (failure: Throwable) {
                    runCatching(session::stop)
                    throw failure
                }
            }
        } finally {
            restoreGuestState()
            seed.delete()
        }
    }

    @Test
    fun downloadsFromParkedPlayerWithoutStartingPlayback() {
        requireFixture()
        instrumentation.runOnMainSync { VehicleLibrarySync.get(context).signOut() }
        preserveGuestState()
        try {
            checkpointStore.save(null, null)
            instrumentation.runOnMainSync { VehicleUserStore(context).currentStory = LibraryItem(STORY_ID, "성수동 수제화 거리") }
            DeviceSession().use { session ->
                session.connect()
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    session.pressControl(scenario) { root -> root.findViewWithTag<View>("focus:mini-player:toggle")?.parent as? View }
                    session.pressControl(scenario) { root -> root.findViewWithTag<View>("focus:player:download-audio") }
                    session.assertDownloadedWithoutPlaying()
                }
                assertTrue(audioCache.find(STORY_ID) != null)
            }
        } finally {
            restoreGuestState()
        }
    }

    private fun requireFixture() = assumeTrue(
        InstrumentationRegistry.getArguments().getString("verifyOfflineAudio") == "true",
    )

    private fun assertFixtureApiOffline() {
        StoryApiClient().use { api ->
            val response = LinkedBlockingQueue<Result<StorySpeech>>()
            api.load(STORY_ID) { response.offer(it) }
            val error = checkNotNull(response.poll(10L, TimeUnit.SECONDS)).exceptionOrNull()
            assertTrue("Stop the fixture API before the offline verification", error?.isConnectionFailure() == true)
        }
    }

    private fun preserveGuestState() {
        restoreGuestState()
        val metadata = JSONObject().put("hadAudio", audioCache.has(STORY_ID))
        for (key in GUEST_METADATA_KEYS) metadata.put(key, guestPreferences.getString(key, null))
        checkpointStore.save(GUEST_BACKUP, checkpointStore.load(null))
        savedGuestMetadata.writeText(metadata.toString())
    }

    private fun restoreGuestState() {
        if (!savedGuestMetadata.isFile) return
        val metadata = JSONObject(savedGuestMetadata.readText())
        val editor = guestPreferences.edit()
        for (key in GUEST_METADATA_KEYS) {
            if (metadata.has(key)) editor.putString(key, metadata.getString(key)) else editor.remove(key)
        }
        check(editor.commit()) { "Could not restore the guest's playback metadata" }
        checkpointStore.save(null, checkpointStore.load(GUEST_BACKUP))
        checkpointStore.save(GUEST_BACKUP, null)
        if (!metadata.getBoolean("hadAudio")) audioCache.remove(STORY_ID)
        savedGuestMetadata.delete()
    }

    private inner class DeviceSession : AutoCloseable {
        private val connected = CountDownLatch(1)
        private val updates = LinkedBlockingQueue<PlaybackState>()
        private val downloadResults = LinkedBlockingQueue<Bundle>()
        private lateinit var browser: MediaBrowser
        private lateinit var controller: MediaController
        private val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                state?.let(updates::offer)
            }

            override fun onSessionEvent(event: String, extras: Bundle?) {
                if (event == PlaybackSessionContract.DOWNLOAD_AUDIO_RESULT) extras?.let(downloadResults::offer)
            }
        }

        fun connect() {
            instrumentation.runOnMainSync {
                browser = MediaBrowser(context, ComponentName(context, RoadStoryPlaybackService::class.java),
                    object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() {
                            controller = MediaController(context, browser.sessionToken).apply {
                                registerCallback(callback)
                                playbackState?.let(updates::offer)
                            }
                            connected.countDown()
                        }
                    }, null)
                browser.connect()
            }
            assertTrue("MediaBrowser connection failed", connected.await(10L, TimeUnit.SECONDS))
        }

        fun command(action: MediaController.TransportControls.() -> Unit) {
            instrumentation.runOnMainSync {
                updates.clear()
                controller.transportControls.action()
            }
        }

        fun awaitState(expected: Int, waitingForPlay: Boolean = false): PlaybackState {
            val deadline = SystemClock.elapsedRealtime() + 25_000L
            while (true) {
                val update = updates.poll((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    ?: throw AssertionError("Timed out waiting for state $expected, current state ${controller.playbackState?.state}")
                if (waitingForPlay) {
                    assertNotEquals("Playback requires an explicit Play action after restart", PlaybackState.STATE_PLAYING, update.state)
                    assertNotEquals("Offline loading requires an explicit Play action after restart", PlaybackState.STATE_BUFFERING, update.state)
                }
                if (update.state == expected) return update
                assertNotEquals("Playback failed while waiting for state $expected", PlaybackState.STATE_ERROR, update.state)
            }
        }

        fun stop() {
            if (controller.playbackState?.state == PlaybackState.STATE_STOPPED) return
            command { stop() }
            awaitState(PlaybackState.STATE_STOPPED)
        }

        fun assertQuiet() {
            val deadline = SystemClock.elapsedRealtime() + 650L
            while (true) {
                val update = updates.poll((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    ?: break
                assertEquals("Playback requires an explicit Play action after restart", PlaybackState.STATE_PAUSED, update.state)
            }
            assertEquals(PlaybackState.STATE_PAUSED, controller.playbackState?.state)
        }

        fun pressPlay(scenario: ActivityScenario<MainActivity>) {
            pressControl(scenario) { root ->
                val play = root.findViewWithTag<View>("focus:mini-player:toggle")
                    ?: root.findViewWithTag<View>("focus:player:toggle")
                play?.takeIf { it.contentDescription?.endsWith("재생") == true }
            }
        }

        fun assertDownloadedWithoutPlaying() {
            val result = checkNotNull(downloadResults.poll(15L, TimeUnit.SECONDS)) { "No download result" }
            assertEquals(STORY_ID, result.getString(PlaybackSessionContract.SELECTED_STORY_ID))
            assertTrue(result.getBoolean(PlaybackSessionContract.DOWNLOAD_SUCCEEDED))
            assertTrue(updates.none { it.state == PlaybackState.STATE_PLAYING || it.state == PlaybackState.STATE_BUFFERING })
        }

        fun pressControl(scenario: ActivityScenario<MainActivity>, find: (View) -> View?) {
            val deadline = SystemClock.elapsedRealtime() + 8_000L
            do {
                var pressed = false
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val control = find(root)
                    if (control != null && control.isShown && control.isEnabled) {
                        updates.clear()
                        pressed = control.performClick()
                    }
                }
                if (pressed) return
                SystemClock.sleep(50L)
            } while (SystemClock.elapsedRealtime() < deadline)
            throw AssertionError("The requested control was unavailable")
        }

        override fun close() {
            instrumentation.runOnMainSync {
                if (::controller.isInitialized) controller.unregisterCallback(callback)
                if (::browser.isInitialized) browser.disconnect()
            }
        }
    }

    private companion object {
        const val STORY_ID = "story-published-1"
        const val GUEST_BACKUP = "offline-audio-test-guest-backup"
        val GUEST_METADATA_KEYS = listOf("currentStory", "history", "savedStories")
        val PlaybackState.storyId get() = extras?.getString(PlaybackSessionContract.CURRENT_ID)
        val PlaybackState.offlineAudio get() = extras?.getBoolean(PlaybackSessionContract.OFFLINE_AUDIO) == true
    }
}
