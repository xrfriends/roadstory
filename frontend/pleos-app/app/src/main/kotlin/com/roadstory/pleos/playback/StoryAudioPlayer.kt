package com.roadstory.pleos.playback

import java.io.File

/** Plays a local audio file. Commands and callbacks run on the playback thread. */
interface StoryAudioPlayer : AutoCloseable {
    val positionMs: Long?

    fun play(
        file: File,
        startPositionMs: Long,
        onStart: (positionMs: Long, durationMs: Long) -> Unit,
        onDone: () -> Unit,
        onError: () -> Unit,
    )

    fun stop()

    override fun close() = stop()
}
