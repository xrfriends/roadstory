package com.roadstory.pleos.playback

import android.content.ComponentName
import android.content.Context
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Process
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.MainActivity
import com.roadstory.pleos.user.VEHICLE_GUEST_NAMESPACE
import com.roadstory.pleos.user.VehicleLibrarySync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Run the two methods separately with a force-stop between them on a parked Pleos emulator. */
@RunWith(AndroidJUnit4::class)
class PlaybackRestartDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val sync = VehicleLibrarySync.get(context)
    private val checkpointStore = PlaybackCheckpointStore(context)
    private val guestPreferences = context.getSharedPreferences(VEHICLE_GUEST_NAMESPACE, Context.MODE_PRIVATE)
    private val savedGuestMetadata = File(context.filesDir, "playback-restart-test-guest")
    private val seedProcess = File(context.filesDir, "playback-restart-test-process")

    @Test
    fun seedBeforeRestart() {
        val credentials = readCredentials()
        seedProcess.delete()
        instrumentation.runOnMainSync { sync.signOut() }
        preserveTestData()
        try {
            DeviceSession().use { session ->
                session.connect()
                try {
                    session.playAndPauseAt(GUEST_SENTENCE)
                    signIn(credentials[0], ACCOUNT_A)
                    session.playAndPauseAt(ACCOUNT_A_SENTENCE)
                } catch (failure: Throwable) {
                    runCatching(session::stop)
                    throw failure
                }
            }
            seedProcess.writeText(Process.myPid().toString())
        } finally {
            if (!seedProcess.isFile) restoreTestData()
        }
    }

    @Test
    fun verifyAfterRestart() {
        val credentials = readCredentials()
        check(seedProcess.isFile) { "Run seedBeforeRestart before restarting the app." }
        assertNotEquals("Verify in a new app process", seedProcess.readText().toInt(), Process.myPid())

        try {
            DeviceSession().use { session ->
                session.connect()
                try {
                    session.assertRestored(GUEST_SENTENCE)

                    signIn(credentials[1], ACCOUNT_B)
                    session.await("account B's empty playback state", waitingForPlay = true) {
                        it.state == PlaybackState.STATE_STOPPED
                    }
                    session.assertEmpty()

                    signIn(credentials[0], ACCOUNT_A)
                    session.verifyExplicitResume(ACCOUNT_A_SENTENCE, pressActivityButton = true)
                    session.stop()

                    instrumentation.runOnMainSync { sync.signOut() }
                    session.verifyExplicitResume(GUEST_SENTENCE)
                } finally {
                    if (sync.status.accountId != ACCOUNT_B) session.stop()
                }
            }
        } finally {
            restoreTestData()
            seedProcess.delete()
        }
    }

    private fun preserveTestData() {
        restoreTestData()
        val metadata = JSONObject()
        for (key in GUEST_METADATA_KEYS) metadata.put(key, guestPreferences.getString(key, null))
        checkpointStore.save(GUEST_BACKUP, checkpointStore.load(null))
        checkpointStore.save(ACCOUNT_A_BACKUP, checkpointStore.load(ACCOUNT_A))
        savedGuestMetadata.writeText(metadata.toString())
    }

    private fun restoreTestData() {
        if (!savedGuestMetadata.isFile) return
        val metadata = JSONObject(savedGuestMetadata.readText())
        val editor = guestPreferences.edit()
        for (key in GUEST_METADATA_KEYS) {
            if (metadata.has(key)) editor.putString(key, metadata.getString(key)) else editor.remove(key)
        }
        check(editor.commit()) { "Could not restore the guest's playback metadata." }
        checkpointStore.save(null, checkpointStore.load(GUEST_BACKUP))
        checkpointStore.save(ACCOUNT_A, checkpointStore.load(ACCOUNT_A_BACKUP))
        checkpointStore.save(GUEST_BACKUP, null)
        checkpointStore.save(ACCOUNT_A_BACKUP, null)
        savedGuestMetadata.delete()
    }

    private fun readCredentials(): List<String> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyPlaybackRestart") == "true")
        val file = File(context.filesDir, "library-sync-test-credentials")
        val credentials = try {
            check(file.isFile) { "Stage two development credentials in app-private storage." }
            file.readLines().filter(String::isNotBlank)
        } finally {
            file.delete()
        }
        assertEquals("Two development credentials are required", 2, credentials.size)
        assertTrue("Use a development sync build", sync.developmentSignInAvailable)
        return credentials
    }

    private fun signIn(credential: String, account: String) {
        instrumentation.runOnMainSync { sync.signInForDevelopment(credential) }
        val deadline = SystemClock.elapsedRealtime() + 15_000
        do {
            var verified = false
            instrumentation.runOnMainSync { verified = sync.status.accountId == account }
            if (verified) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("The development account could not be verified")
    }

    private inner class DeviceSession : AutoCloseable {
        private val connected = CountDownLatch(1)
        private val updates = LinkedBlockingQueue<PlaybackState>()
        private lateinit var browser: MediaBrowser
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
                            controller = MediaController(context, browser.sessionToken).apply {
                                registerCallback(callback)
                                playbackState?.let(updates::offer)
                            }
                            connected.countDown()
                        }
                    }, null)
                browser.connect()
            }
            assertTrue("MediaBrowser connection failed", connected.await(10, TimeUnit.SECONDS))
        }

        fun playAndPauseAt(index: Int) {
            command { playFromMediaId(STORY_ID, null) }
            await("the requested sentence to start") {
                it.state == PlaybackState.STATE_PLAYING && it.storyId == STORY_ID && it.sentenceIndex == index
            }
            SystemClock.sleep(200)
            command { pause() }
            val paused = await("the requested sentence to pause") {
                it.state == PlaybackState.STATE_PAUSED && it.storyId == STORY_ID && it.sentenceIndex == index
            }
            assertPausedCheckpoint(paused)
        }

        fun assertRestored(index: Int): PlaybackState {
            val restored = await("the persisted sentence to restore", waitingForPlay = true) {
                it.state == PlaybackState.STATE_PAUSED && it.storyId == STORY_ID && it.sentenceIndex == index
            }
            assertPausedCheckpoint(restored)
            assertQuiet()
            assertEquals(index, currentState().sentenceIndex)
            assertEquals(restored.listenedMs, currentState().listenedMs)
            return restored
        }

        fun assertEmpty() {
            val state = currentState()
            assertEquals(PlaybackState.STATE_STOPPED, state.state)
            assertEquals(-1, state.sentenceIndex)
            assertEquals(false, state.resumeAtSentence)
            assertQuiet()
        }

        fun verifyExplicitResume(index: Int, pressActivityButton: Boolean = false) {
            val restored = assertRestored(index)
            val resumed = if (pressActivityButton) {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    pressPlay(scenario)
                    awaitPlaying()
                }
            } else {
                command { play() }
                awaitPlaying()
            }
            assertEquals(STORY_ID, resumed.storyId)
            assertEquals(index, resumed.sentenceIndex)
            assertEquals(restored.listenedMs, resumed.listenedMs)
        }

        private fun awaitPlaying() = await("the first audible sentence after explicit Play") {
            it.state == PlaybackState.STATE_PLAYING
        }

        private fun pressPlay(scenario: ActivityScenario<MainActivity>) {
            val deadline = SystemClock.elapsedRealtime() + 8_000
            do {
                var pressed = false
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val play = root.findViewWithTag<View>("focus:mini-player:toggle")
                        ?: root.findViewWithTag<View>("focus:player:toggle")
                    if (play != null && play.isShown && play.isEnabled && play.contentDescription?.endsWith("재생") == true) {
                        updates.clear()
                        pressed = play.performClick()
                    }
                }
                if (pressed) return
                SystemClock.sleep(50)
            } while (SystemClock.elapsedRealtime() < deadline)
            throw AssertionError("The Play button was unavailable")
        }

        fun stop() {
            if (currentState().state != PlaybackState.STATE_STOPPED) {
                command { stop() }
                await("playback to stop") { it.state == PlaybackState.STATE_STOPPED }
            }
            assertEmpty()
        }

        private fun assertPausedCheckpoint(state: PlaybackState) {
            assertTrue("Paused state must be resumable", state.resumeAtSentence)
            assertTrue("The checkpoint must include elapsed listening time", state.listenedMs > 0)
            assertEquals(-1L, state.extras?.getLong(PlaybackSessionContract.LISTENING_SINCE_ELAPSED_MS, -1L))
        }

        private fun assertQuiet() {
            val deadline = SystemClock.elapsedRealtime() + 650
            while (true) {
                val update = updates.poll((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    ?: break
                assertInactive(update)
            }
            assertInactive(currentState())
        }

        private fun assertInactive(state: PlaybackState) {
            assertNotEquals("Playback requires an explicit Play command", PlaybackState.STATE_PLAYING, state.state)
            assertNotEquals("Playback must wait before loading the Story", PlaybackState.STATE_BUFFERING, state.state)
        }

        fun await(
            description: String,
            waitingForPlay: Boolean = false,
            matches: (PlaybackState) -> Boolean,
        ): PlaybackState {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (true) {
                val state = updates.poll((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    ?: throw AssertionError("Timed out waiting for $description (state ${currentState().state})")
                if (waitingForPlay) assertInactive(state)
                if (matches(state)) return state
                if (state.state == PlaybackState.STATE_ERROR) throw AssertionError("Playback failed while waiting for $description")
            }
        }

        private fun command(action: MediaController.TransportControls.() -> Unit) {
            instrumentation.runOnMainSync {
                updates.clear()
                checkNotNull(controller).transportControls.action()
            }
        }

        private fun currentState() = checkNotNull(checkNotNull(controller).playbackState)

        override fun close() {
            instrumentation.runOnMainSync {
                controller?.unregisterCallback(callback)
                if (::browser.isInitialized) browser.disconnect()
            }
        }
    }

    private companion object {
        const val STORY_ID = "story-published-1"
        const val ACCOUNT_A = "dev-account-a"
        const val ACCOUNT_B = "dev-account-b"
        const val GUEST_SENTENCE = 0
        const val ACCOUNT_A_SENTENCE = 1
        const val GUEST_BACKUP = "playback-restart-test-guest-backup"
        const val ACCOUNT_A_BACKUP = "playback-restart-test-a-backup"
        val GUEST_METADATA_KEYS = listOf("currentStory", "history", "savedStories")
        val PlaybackState.storyId get() = extras?.getString(PlaybackSessionContract.CURRENT_ID)
        val PlaybackState.sentenceIndex get() = extras?.getInt(PlaybackSessionContract.CURRENT_SENTENCE_INDEX, -1) ?: -1
        val PlaybackState.listenedMs get() = extras?.getLong(PlaybackSessionContract.LISTENED_MS) ?: -1L
        val PlaybackState.resumeAtSentence get() = extras?.getBoolean(PlaybackSessionContract.RESUME_AT_SENTENCE) == true
    }
}
