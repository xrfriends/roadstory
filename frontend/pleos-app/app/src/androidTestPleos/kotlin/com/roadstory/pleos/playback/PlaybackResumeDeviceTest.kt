package com.roadstory.pleos.playback

import android.content.ComponentName
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Requires a parked Pleos emulator and the local published Story API. */
@RunWith(AndroidJUnit4::class)
class PlaybackResumeDeviceTest {
    @Test fun pauseFreezesListenedTimeAndPlayContinuesTheSameStory() = withSession { session ->
        session.startStory()
        SystemClock.sleep(650)
        session.command { pause() }
        val paused = session.await(PlaybackState.STATE_PAUSED)
        assertCheckpoint(paused)
        SystemClock.sleep(350)
        assertEquals(paused.listenedMs, session.state().listenedMs)
        session.command { play() }
        assertResumed(paused, session.await(PlaybackState.STATE_PLAYING))
    }

    @Test fun navigationFocusPausesAndRestoresPlayback() = withSession { session ->
        session.startStory()
        SystemClock.sleep(500)
        val request = session.focusRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        session.requestFocus(request)
        val paused = try {
            session.await(PlaybackState.STATE_PAUSED).also(::assertCheckpoint)
        } finally {
            session.abandonFocus(request)
        }
        assertResumed(paused, session.await(PlaybackState.STATE_PLAYING))
    }

    @Test fun callFocusResumesUnlessTheUserStops() = withSession { session ->
        session.startStory()
        SystemClock.sleep(500)
        val request = session.focusRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, AudioAttributes.USAGE_VOICE_COMMUNICATION)
        session.requestFocus(request)
        val paused = try {
            session.await(PlaybackState.STATE_PAUSED).also(::assertCheckpoint)
        } finally {
            session.abandonFocus(request)
        }
        assertResumed(paused, session.await(PlaybackState.STATE_PLAYING))
        session.requestFocus(request)
        try {
            assertCheckpoint(session.await(PlaybackState.STATE_PAUSED))
            session.command { stop() }
            val stopped = session.await(PlaybackState.STATE_STOPPED)
            assertEquals(0L, stopped.listenedMs)
        } finally {
            session.abandonFocus(request)
        }
        SystemClock.sleep(750)
        assertEquals(PlaybackState.STATE_STOPPED, session.state().state)
    }

    @Test fun screenOffKeepsCheckpointAndWakeRequiresExplicitPlay() = withSession { session ->
        session.startStory()
        SystemClock.sleep(500)
        session.powerKey("KEYCODE_SLEEP")
        val paused = try {
            session.await(PlaybackState.STATE_PAUSED).also(::assertCheckpoint)
        } finally {
            session.powerKey("KEYCODE_WAKEUP")
        }
        SystemClock.sleep(500)
        assertEquals(PlaybackState.STATE_PAUSED, session.state().state)
        assertEquals(paused.listenedMs, session.state().listenedMs)
        session.command { play() }
        assertResumed(paused, session.await(PlaybackState.STATE_PLAYING))
    }

    private fun assertCheckpoint(state: PlaybackState) {
        assertEquals(STORY_ID, state.extras?.getString(PlaybackSessionContract.CURRENT_ID))
        assertEquals(PlaybackState.PLAYBACK_POSITION_UNKNOWN, state.position)
        assertTrue(state.extras?.getBoolean(PlaybackSessionContract.RESUME_AT_SENTENCE) == true)
        assertEquals(-1L, state.listeningSince)
        assertTrue(state.listenedMs > 0)
    }

    private fun assertResumed(paused: PlaybackState, resumed: PlaybackState) {
        assertEquals(STORY_ID, resumed.extras?.getString(PlaybackSessionContract.CURRENT_ID))
        assertEquals(paused.listenedMs, resumed.listenedMs)
        assertTrue(resumed.listeningSince >= 0)
    }

    private fun withSession(test: (DeviceSession) -> Unit) = DeviceSession().use { session ->
        session.connect()
        test(session)
    }

    private class DeviceSession : AutoCloseable {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context = instrumentation.targetContext
        private val audioManager = checkNotNull(context.getSystemService(AudioManager::class.java))
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
                            controller = MediaController(context, browser.sessionToken).apply { registerCallback(callback) }
                            connected.countDown()
                        }
                    }, null)
                browser.connect()
            }
            assertTrue("MediaBrowser connection failed", connected.await(10, TimeUnit.SECONDS))
        }

        fun startStory() {
            command { playFromMediaId(STORY_ID, null) }
            val playing = await(PlaybackState.STATE_PLAYING)
            assertEquals(STORY_ID, playing.extras?.getString(PlaybackSessionContract.CURRENT_ID))
            assertTrue(playing.listeningSince >= 0)
        }

        fun command(action: MediaController.TransportControls.() -> Unit) {
            instrumentation.runOnMainSync { checkNotNull(controller).transportControls.action() }
        }

        fun state(): PlaybackState = checkNotNull(checkNotNull(controller).playbackState)

        fun await(expected: Int): PlaybackState {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (true) {
                val state = updates.poll((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
                    ?: throw AssertionError("Timed out waiting for playback state $expected, observed ${state().state}")
                if (state.state == expected) return state
                if (state.state == PlaybackState.STATE_ERROR) throw AssertionError("Playback failed: ${state.errorMessage}")
            }
        }

        fun focusRequest(gain: Int, usage: Int): AudioFocusRequest = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { }
            .build()

        fun requestFocus(request: AudioFocusRequest) {
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(request))
        }

        fun abandonFocus(request: AudioFocusRequest) {
            audioManager.abandonAudioFocusRequest(request)
        }

        fun powerKey(key: String) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent $key"))
                .use { it.readBytes() }
        }

        override fun close() {
            instrumentation.runOnMainSync {
                controller?.transportControls?.stop()
                controller?.unregisterCallback(callback)
                if (::browser.isInitialized) browser.disconnect()
            }
        }
    }

    companion object {
        private const val STORY_ID = "story-published-1"
        private val PlaybackState.listenedMs get() = extras?.getLong(PlaybackSessionContract.LISTENED_MS) ?: -1L
        private val PlaybackState.listeningSince get() = extras?.getLong(PlaybackSessionContract.LISTENING_SINCE_ELAPSED_MS) ?: -1L
    }
}
