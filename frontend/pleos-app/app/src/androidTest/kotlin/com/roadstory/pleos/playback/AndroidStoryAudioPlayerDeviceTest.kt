package com.roadstory.pleos.playback

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AndroidStoryAudioPlayerDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun seeksToSavedPositionBeforeStartingLocalPlayback() = withPlayer { player, file ->
        val firstStart = start(player, file, 1_200L)
        assertEquals(8_000L, firstStart.durationMs)
        assertTrue("Initial seek reached ${firstStart.positionMs} ms", abs(firstStart.positionMs - 1_200L) <= 100L)

        val savedPosition = waitForPosition(player, afterMs = firstStart.positionMs + 150L)
        instrumentation.runOnMainSync {
            player.stop()
            assertNull(player.positionMs)
        }

        val resumed = start(player, file, savedPosition)
        assertEquals(firstStart.durationMs, resumed.durationMs)
        assertTrue("Resumed at ${resumed.positionMs} ms from $savedPosition ms", abs(resumed.positionMs - savedPosition) <= 100L)
        waitForPosition(player, afterMs = resumed.positionMs + 150L)
    }

    @Test
    fun releasesPendingPlaybackWithoutDeliveringCallbacks() = withPlayer { player, file ->
        val staleCallback = CountDownLatch(1)
        instrumentation.runOnMainSync {
            player.play(file, 1_200L, { _, _ -> staleCallback.countDown() }, staleCallback::countDown, staleCallback::countDown)
            player.stop()
            assertNull(player.positionMs)
        }
        assertEquals(0L, start(player, file, 0L).positionMs)
        assertFalse("Released playback delivered a callback", staleCallback.await(400L, TimeUnit.MILLISECONDS))
    }

    private fun withPlayer(block: (AndroidStoryAudioPlayer, File) -> Unit) {
        val file = createAudioFixture()
        val player = AndroidStoryAudioPlayer()
        try {
            block(player, file)
        } finally {
            instrumentation.runOnMainSync { player.close() }
            file.delete()
        }
    }

    private data class Start(val positionMs: Long, val durationMs: Long)

    private fun start(player: AndroidStoryAudioPlayer, file: File, offset: Long): Start {
        val result = LinkedBlockingQueue<Result<Start>>()
        instrumentation.runOnMainSync {
            player.play(file, offset, { position, duration -> result.offer(Result.success(Start(position, duration))) },
                { result.offer(Result.failure(AssertionError("Audio finished before starting"))) },
                { result.offer(Result.failure(AssertionError("Local audio could not start"))) })
        }
        return checkNotNull(result.poll(10L, TimeUnit.SECONDS)) { "Audio start timed out" }.getOrThrow()
    }

    private fun waitForPosition(player: AndroidStoryAudioPlayer, afterMs: Long): Long {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            var position: Long? = null
            instrumentation.runOnMainSync { position = player.positionMs }
            position?.takeIf { it > afterMs }?.let { return it }
            SystemClock.sleep(50L)
        }
        throw AssertionError("Playback position did not advance beyond $afterMs ms")
    }

    private fun createAudioFixture(): File {
        val sampleRate = 16_000
        val pcmBytes = 8 * sampleRate * 2
        val wav = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcmBytes)
        wav.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        wav.putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        wav.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcmBytes)
        return File.createTempFile("story-audio", ".wav", instrumentation.targetContext.cacheDir).apply { writeBytes(wav.array()) }
    }
}
