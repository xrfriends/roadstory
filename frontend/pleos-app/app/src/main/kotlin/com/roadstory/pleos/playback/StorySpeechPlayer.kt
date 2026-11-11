package com.roadstory.pleos.playback

interface StorySpeechPlayer : AutoCloseable {
    val isPlaying: Boolean
    val positionMs: Long get() = 0L

    fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit, onError: (String) -> Unit)
    fun stop()
    fun release()
    override fun close() = release()
}
