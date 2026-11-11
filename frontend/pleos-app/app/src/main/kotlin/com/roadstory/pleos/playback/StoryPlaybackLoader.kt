package com.roadstory.pleos.playback

import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class StoryPlaybackLoader(private val api: StoryApiClient, private val cache: StoryAudioCache) {
    fun load(storyId: String, callback: (Result<StorySpeech>) -> Unit) = api.load(storyId) { result ->
        callback(runCatching {
            val story = result.getOrElse { failure ->
                if (failure.isConnectionFailure()) {
                    val cached = cache.find(storyId) ?: throw OfflineAudioUnavailable()
                    return@runCatching cached.toOfflineStory()
                }
                if (failure is StoryApiResponseFailure && failure.status in setOf(404, 410)) cache.remove(storyId)
                throw failure
            }
            require(story.id == storyId)
            val asset = story.audio
            if (asset == null) {
                cache.remove(storyId)
                story
            } else {
                story.copy(localAudio = cache.fetch(story.toQueueStory(), asset))
            }
        })
    }

    fun download(storyId: String, callback: (Result<Unit>) -> Unit) = load(storyId) { result ->
        callback(result.mapCatching { story ->
            if (story.localAudio == null) throw OfflineAudioUnavailable()
            Unit
        })
    }

    private fun CachedStoryAudio.toOfflineStory() = StorySpeech(
        story.id, story.title, "", story.placeName, story.durationSeconds, story.category, story.familyFriendly,
        audio = asset, localAudio = this, offlineAudio = true,
    )
}

internal class OfflineAudioUnavailable : RuntimeException()

internal fun Throwable.isConnectionFailure() =
    this is SocketException || this is SocketTimeoutException || this is UnknownHostException
