package com.roadstory.pleos.playback

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Looper
import java.io.File
import java.io.IOException

/** Uses the platform decoder on the main looper. Audio focus belongs to the playback service. */
internal class AndroidStoryAudioPlayer : StoryAudioPlayer {
    private var activePlayer: MediaPlayer? = null
    private var started = false

    override val positionMs: Long?
        get() {
            checkMainThread()
            if (!started) return null
            return try {
                activePlayer?.currentPosition?.toLong()
            } catch (_: IllegalStateException) {
                null
            }
        }

    override fun play(
        file: File,
        startPositionMs: Long,
        onStart: (Long, Long) -> Unit,
        onDone: () -> Unit,
        onError: () -> Unit,
    ) {
        stop()
        val player = try {
            MediaPlayer()
        } catch (_: RuntimeException) {
            onError()
            return
        }
        activePlayer = player
        try {
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            player.setOnPreparedListener { prepared ->
                if (activePlayer === prepared) prepareStart(prepared, startPositionMs, onStart, onError)
            }
            player.setOnCompletionListener { completed -> finish(completed, onDone) }
            player.setOnErrorListener { failed, _, _ ->
                finish(failed, onError)
                true
            }
            file.inputStream().use { player.setDataSource(it.fd) }
            player.prepareAsync()
        } catch (_: IOException) {
            finish(player, onError)
        } catch (_: RuntimeException) {
            finish(player, onError)
        }
    }

    private fun prepareStart(
        player: MediaPlayer,
        startPositionMs: Long,
        onStart: (Long, Long) -> Unit,
        onError: () -> Unit,
    ) {
        val durationMs = try {
            player.duration.toLong()
        } catch (_: IllegalStateException) {
            finish(player, onError)
            return
        }
        if (durationMs <= 0L) {
            finish(player, onError)
            return
        }
        if (startPositionMs !in 1L until durationMs) {
            start(player, durationMs, onStart, onError)
            return
        }
        try {
            player.setOnSeekCompleteListener { positioned ->
                if (activePlayer === positioned && !started) start(positioned, durationMs, onStart, onError)
            }
            player.seekTo(startPositionMs, MediaPlayer.SEEK_CLOSEST)
        } catch (_: RuntimeException) {
            finish(player, onError)
        }
    }

    private fun start(player: MediaPlayer, durationMs: Long, onStart: (Long, Long) -> Unit, onError: () -> Unit) {
        val offset = try {
            val positionMs = player.currentPosition.toLong()
            player.start()
            positionMs
        } catch (_: RuntimeException) {
            finish(player, onError)
            return
        }
        started = true
        onStart(offset, durationMs)
    }

    private fun finish(player: MediaPlayer, callback: () -> Unit) {
        if (activePlayer !== player) return
        stop()
        callback()
    }

    override fun stop() {
        checkMainThread()
        val player = activePlayer
        activePlayer = null
        started = false
        player?.release()
    }

    private fun checkMainThread() = check(Looper.myLooper() === Looper.getMainLooper())
}
