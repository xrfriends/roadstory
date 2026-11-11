package com.roadstory.pleos.playback

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

fun createStorySpeechPlayer(context: Context): StorySpeechPlayer = AndroidStorySpeechPlayer(context.applicationContext)

private class AndroidStorySpeechPlayer(private val context: Context) : StorySpeechPlayer {
    private data class Request(
        val id: String,
        val text: String,
        val onStart: () -> Unit,
        val onDone: () -> Unit,
        val onError: (String) -> Unit,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var engineGeneration = 0L
    private var requestSequence = 0L
    private var pending: Request? = null
    override val isPlaying: Boolean get() = pending != null

    override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val request = Request("road-story-${++requestSequence}", text, onStart, onDone, onError)
        pending = request
        if (ready) startSpeaking(request) else ensureInitialized()
    }

    private fun ensureInitialized() {
        if (tts != null) return
        val generation = ++engineGeneration
        tts = TextToSpeech(context) { status ->
            mainHandler.post {
                if (generation != engineGeneration) return@post
                val engine = tts ?: return@post
                if (status != TextToSpeech.SUCCESS) {
                    val request = pending
                    release()
                    request?.onError?.invoke("Android TTS initialization failed")
                    return@post
                }
                ready = true
                engine.language = Locale.KOREA
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    private fun deliver(id: String?, finished: Boolean = false, event: (Request) -> Unit) {
                        mainHandler.post {
                            val request = pending
                            if (request != null && request.id == id && generation == engineGeneration) {
                                if (finished) pending = null
                                event(request)
                            }
                        }
                    }

                    override fun onStart(utteranceId: String?) = deliver(utteranceId) { it.onStart() }
                    override fun onDone(utteranceId: String?) = deliver(utteranceId, finished = true) { it.onDone() }

                    @Deprecated("Deprecated in Android")
                    override fun onError(utteranceId: String?) = deliver(utteranceId, finished = true) {
                        it.onError("Android TTS synthesis failed")
                    }
                })
                pending?.let(::startSpeaking)
            }
        }
    }

    private fun startSpeaking(request: Request) {
        if (tts?.speak(request.text, TextToSpeech.QUEUE_FLUSH, Bundle(), request.id) == TextToSpeech.ERROR) {
            pending = null
            request.onError("Android TTS synthesis failed")
        }
    }

    override fun stop() {
        pending = null
        tts?.stop()
    }

    override fun release() {
        engineGeneration++
        pending = null
        ready = false
        val engine = tts
        tts = null
        engine?.shutdown()
    }
}
