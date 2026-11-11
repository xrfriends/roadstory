package com.roadstory.pleos.playback

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import java.util.Locale

fun createStorySpeechPlayer(context: Context): StorySpeechPlayer = PleosStorySpeechPlayer(context.applicationContext)

private class PleosStorySpeechPlayer(private val context: Context) : StorySpeechPlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var listener: EventListener? = null
    override val isPlaying: Boolean get() = listener != null

    override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        try {
            val engine = ensureInitialized()
            detachListener(engine)
            val events = object : EventListener {
                private fun deliver(finished: Boolean = false, event: () -> Unit) {
                    val requestListener = this
                    mainHandler.post {
                        // A replaced or released utterance cannot complete a new queue item.
                        if (tts === engine && listener === requestListener) {
                            if (finished) detachListener(engine)
                            event()
                        }
                    }
                }

                override fun onReady() = Unit
                override fun onStart() = deliver(event = onStart)
                override fun onDone() = deliver(finished = true, event = onDone)
                override fun onStop() = Unit
                override fun onError(message: String) = deliver(finished = true) { onError.invoke(message) }
                override fun onUpdatedRms(rms: Double) = Unit
            }
            listener = events
            engine.addEventListener(events)
            // The installed SDK queues the final request while its service is binding.
            engine.speak(text, true)
        } catch (failure: RuntimeException) {
            runCatching { release() }
            onError("TTS_INITIALIZATION_${failure.javaClass.simpleName}")
        }
    }

    private fun ensureInitialized(): TextToSpeech {
        tts?.let { return it }
        return TextToSpeech(context, Mode.ON_DEVICE).also { engine ->
            tts = engine
            // The installed 2.1.5.1 SDK must be initialized before listener/configuration calls.
            engine.initialize()
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            engine.setLocale(Locale.KOREA)
        }
    }

    private fun detachListener(engine: TextToSpeech) {
        val previous = listener
        listener = null
        previous?.let(engine::removeEventListener)
    }

    override fun stop() {
        tts?.let { engine ->
            try {
                detachListener(engine)
            } finally {
                engine.stop()
            }
        }
    }

    override fun release() {
        val engine = tts ?: return
        tts = null
        try {
            detachListener(engine)
        } finally {
            engine.release()
        }
    }
}
