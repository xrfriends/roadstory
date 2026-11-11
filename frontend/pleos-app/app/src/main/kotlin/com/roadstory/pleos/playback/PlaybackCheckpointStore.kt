package com.roadstory.pleos.playback

import android.content.Context
import com.roadstory.pleos.user.ownerKey
import org.json.JSONObject

/** One local playback checkpoint for each verified account and the guest profile. */
internal class PlaybackCheckpointStore(context: Context) {
    private val preferences = context.getSharedPreferences("road_story_playback_checkpoint", Context.MODE_PRIVATE)
    private val uncommittedKeys = mutableSetOf<String>()

    fun load(accountId: String?): PlaybackCheckpoint? {
        val stored = read(keyFor(accountId)) ?: return null
        if (stored.length > MAX_RECORD_LENGTH) return null
        return runCatching {
            val record = JSONObject(stored)
            require(record.get("version") == 1)
            val story = record.getJSONObject("story")
            PlaybackCheckpoint(
                story = QueueStory(
                    id = story.get("id") as String,
                    title = story.get("title") as String,
                    placeName = story.get("placeName") as String,
                    durationSeconds = story.get("durationSeconds") as Int,
                    category = if (story.isNull("category")) null else story.get("category") as String,
                    familyFriendly = story.get("familyFriendly") as Boolean,
                ),
                narrationFingerprint = record.get("narrationFingerprint") as String,
                sentenceIndex = record.get("sentenceIndex") as Int,
                listenedMs = record.readMillis("listenedMs"),
                audioPositionMs = if (record.has("audioPositionMs")) record.readMillis("audioPositionMs") else null,
                audioDurationMs = if (record.has("audioDurationMs")) record.readMillis("audioDurationMs") else null,
            ).takeIf { it.isValid() }
        }.getOrNull()
    }

    fun save(accountId: String?, checkpoint: PlaybackCheckpoint?) {
        val key = keyFor(accountId)
        val stored = checkpoint?.toStoredRecord()
        if (checkpoint != null && stored == null) return
        if (key !in uncommittedKeys && read(key) == stored && (stored != null || !preferences.contains(key))) return

        val editor = preferences.edit()
        if (stored == null) editor.remove(key) else editor.putString(key, stored)
        // A sentence boundary or explicit pause must survive immediate process termination.
        if (editor.commit()) uncommittedKeys.remove(key) else uncommittedKeys.add(key)
    }

    private fun read(key: String): String? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    private fun keyFor(accountId: String?): String = accountId?.let { "account.${ownerKey(it)}" } ?: "guest"

    private fun PlaybackCheckpoint.toStoredRecord(): String? {
        val bounded = copy(story = story.copy(
            title = story.title.take(MAX_LABEL_LENGTH),
            placeName = story.placeName.take(MAX_LABEL_LENGTH),
            category = story.category?.take(MAX_CATEGORY_LENGTH),
        ))
        if (!bounded.isValid()) return null
        val publicStory = bounded.story
        return JSONObject()
            .put("version", 1)
            .put("story", JSONObject()
                .put("id", publicStory.id)
                .put("title", publicStory.title)
                .put("placeName", publicStory.placeName)
                .put("durationSeconds", publicStory.durationSeconds)
                .put("category", publicStory.category)
                .put("familyFriendly", publicStory.familyFriendly))
            .put("narrationFingerprint", bounded.narrationFingerprint)
            .put("sentenceIndex", bounded.sentenceIndex)
            .put("listenedMs", bounded.listenedMs)
            .put("audioPositionMs", bounded.audioPositionMs)
            .put("audioDurationMs", bounded.audioDurationMs)
            .toString()
    }

    private fun PlaybackCheckpoint.isValid() =
        story.id.isNotBlank() && story.id.length <= MAX_STORY_ID_LENGTH &&
            story.title.length <= MAX_LABEL_LENGTH && story.placeName.length <= MAX_LABEL_LENGTH &&
            (story.category?.length ?: 0) <= MAX_CATEGORY_LENGTH && story.durationSeconds >= 0 &&
            narrationFingerprint.matches(FINGERPRINT) && sentenceIndex >= 0 && listenedMs in 0..MAX_LISTENED_MS &&
            when {
                audioPositionMs == null -> audioDurationMs == null
                audioDurationMs == null -> false
                else -> audioDurationMs <= Int.MAX_VALUE && audioPositionMs in 0 until audioDurationMs
            }

    private fun JSONObject.readMillis(key: String): Long = when (val value = get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> error("Invalid playback time")
    }

    private companion object {
        const val MAX_STORY_ID_LENGTH = 128
        const val MAX_LABEL_LENGTH = 512
        const val MAX_CATEGORY_LENGTH = 128
        const val MAX_RECORD_LENGTH = 8_192
        const val MAX_LISTENED_MS = Long.MAX_VALUE / 2
        val FINGERPRINT = Regex("[0-9a-f]{64}")
    }
}
