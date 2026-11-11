package com.roadstory.pleos.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/** Exercises the real PLEOS engine, including SDK initialization and listener order. */
@RunWith(AndroidJUnit4::class)
class StorySpeechPlayerDeviceTest {
    @Test fun consecutiveKoreanRequestsCompleteWithoutEngineError() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var player: StorySpeechPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = createStorySpeechPlayer(instrumentation.targetContext)
            }
            repeat(2) { request ->
                val started = AtomicBoolean(false)
                val finished = CountDownLatch(1)
                val error = AtomicReference<String>()
                instrumentation.runOnMainSync {
                    player?.speak("로드스토리 재생 확인입니다.", { started.set(true) },
                        { finished.countDown() }, { error.set(it); finished.countDown() })
                }
                assertTrue("Request $request did not finish", finished.await(20, TimeUnit.SECONDS))
                assertTrue("Request $request failed: ${error.get()}", error.get() == null)
                assertTrue("Request $request never started", started.get())
            }
        } finally {
            instrumentation.runOnMainSync { player?.release() }
        }
    }
}
