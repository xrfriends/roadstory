package com.roadstory.pleos.playback

import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.user.VehicleUserStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Integration check: requires the local published Story API and a PLEOS emulator. */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceDeviceTest {
    @Test fun publishedStoryPlaysRecordsHistoryAndPauses() = verifyPlayback(PlaybackSource.SELECTED) {
        playFromMediaId("story-published-1", null)
    }

    @Test fun courseSelectionUsesItsPublishedStoryWithoutLocation() = verifyPlayback(PlaybackSource.COURSE) {
        sendCustomAction(PlaybackSessionContract.START_COURSE, Bundle().apply {
            putString(PlaybackSessionContract.COURSE_ID, "course-seongsu")
            putString(PlaybackSessionContract.SELECTED_STORY_ID, "story-published-1")
        })
    }

    private fun verifyPlayback(expectedSource: PlaybackSource, start: MediaController.TransportControls.() -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val connected = CountDownLatch(1)
        val playing = CountDownLatch(1)
        val paused = CountDownLatch(1)
        var controller: MediaController? = null
        var browser: MediaBrowser? = null
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (state?.state == PlaybackState.STATE_PLAYING &&
                    state.extras?.getString(PlaybackSessionContract.CURRENT_ID) == "story-published-1" &&
                    state.extras?.getString(PlaybackSessionContract.SOURCE) == expectedSource.name) playing.countDown()
                if (state?.state == PlaybackState.STATE_PAUSED && playing.count == 0L) paused.countDown()
            }
        }
        try {
            instrumentation.runOnMainSync {
                browser = MediaBrowser(context, ComponentName(context, RoadStoryPlaybackService::class.java),
                    object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() {
                            controller = MediaController(context, checkNotNull(browser).sessionToken).apply { registerCallback(callback) }
                            connected.countDown()
                        }
                    }, null).also { it.connect() }
            }
            assertTrue("MediaBrowser connection failed", connected.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { checkNotNull(controller).transportControls.start() }
            assertTrue("Published Story did not reach PLAYING", playing.await(20, TimeUnit.SECONDS))
            assertTrue(VehicleUserStore(context).history.any { it.id == "story-published-1" })
            instrumentation.runOnMainSync { controller?.transportControls?.pause() }
            assertTrue("Pause did not reach PAUSED", paused.await(5, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                controller?.transportControls?.stop()
                controller?.unregisterCallback(callback)
                browser?.disconnect()
            }
        }
    }
}
