package com.roadstory.pleos.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

class AudioFocusController(context: Context, private val onFocusChanged: (Int) -> Unit) : AutoCloseable {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private data class FocusLease(val token: Any, val request: AudioFocusRequest, var granted: Boolean)
    private var lease: FocusLease? = null

    fun request(): Boolean {
        lease?.let { return it.granted }
        // Android dispatches focus changes by listener identity. Never reuse it after abandon.
        val token = Any()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                lease?.takeIf { it.token === token }?.let { active ->
                    active.granted = change == AudioManager.AUDIOFOCUS_GAIN
                    onFocusChanged(change)
                }
            }
            .build()
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) lease = FocusLease(token, request, true)
        else audioManager.abandonAudioFocusRequest(request)
        return granted
    }

    fun abandon() {
        val request = lease?.request ?: return
        lease = null
        audioManager.abandonAudioFocusRequest(request)
    }

    override fun close() = abandon()
}
