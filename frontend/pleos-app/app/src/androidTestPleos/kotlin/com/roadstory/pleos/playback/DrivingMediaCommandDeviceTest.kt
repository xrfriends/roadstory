package com.roadstory.pleos.playback

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadstory.pleos.platform.CarUxRestrictionSource
import com.roadstory.pleos.platform.FailClosedDrivingRestrictionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** TS-DRIVE-004. Requires a driving PLEOS emulator and the local published Story API. */
@RunWith(AndroidJUnit4::class)
class DrivingMediaCommandDeviceTest {
    @Test
    fun drivingBlocksExternalStorySelectionAndAllowsPlaybackAndStop() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyDrivingMediaSession") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val restrictions = FailClosedDrivingRestrictionSource { CarUxRestrictionSource(context) }
        val driving = CountDownLatch(1)
        val connected = CountDownLatch(1)
        val startedOrFailed = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        var browser: MediaBrowser? = null
        var controller: MediaController? = null
        var requestedPlayback = false
        var playbackResult = PlaybackState.STATE_NONE
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (!requestedPlayback) return
                val current = state?.state ?: return
                if (startedOrFailed.count != 0L &&
                    (current == PlaybackState.STATE_PLAYING || current == PlaybackState.STATE_ERROR)
                ) {
                    playbackResult = current
                    startedOrFailed.countDown()
                }
                if (playbackResult == PlaybackState.STATE_PLAYING && current == PlaybackState.STATE_STOPPED) {
                    stopped.countDown()
                }
            }
        }
        try {
            instrumentation.runOnMainSync {
                restrictions.observe {
                    // The fail-closed startup value has zero flags. Wait for the actual AAOS restriction.
                    if (it.activeRestrictions != 0) driving.countDown()
                }
                browser = MediaBrowser(
                    context,
                    ComponentName(context, RoadStoryPlaybackService::class.java),
                    object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() {
                            controller = MediaController(context, checkNotNull(browser).sessionToken).apply {
                                registerCallback(callback)
                            }
                            connected.countDown()
                        }
                    },
                    null,
                ).also { it.connect() }
            }
            assertTrue("Set the PLEOS emulator to driving before running this test", driving.await(10, TimeUnit.SECONDS))
            assertTrue("MediaBrowser connection failed", connected.await(10, TimeUnit.SECONDS))
            val mediaController = checkNotNull(controller)
            instrumentation.runOnMainSync {
                assertTrue("AAOS restriction was lost", restrictions.current().activeRestrictions != 0)
                requestedPlayback = true
                mediaController.transportControls.playFromMediaId("missing-${UUID.randomUUID()}", null)
                mediaController.transportControls.play()
            }
            assertTrue("No playback result", startedOrFailed.await(20, TimeUnit.SECONDS))
            assertEquals("Current Story did not reach PLAYING", PlaybackState.STATE_PLAYING, playbackResult)
            assertEquals("story-published-1", mediaController.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
            instrumentation.runOnMainSync { mediaController.transportControls.stop() }
            assertTrue("Stop did not reach STOPPED", stopped.await(5, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                controller?.transportControls?.stop()
                controller?.unregisterCallback(callback)
                browser?.disconnect()
                restrictions.close()
            }
        }
    }
}
