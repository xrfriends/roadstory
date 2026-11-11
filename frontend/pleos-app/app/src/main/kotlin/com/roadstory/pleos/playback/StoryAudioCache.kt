package com.roadstory.pleos.playback

import android.content.Context
import com.roadstory.pleos.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class AudioAsset(
    val url: String,
    val contentType: String,
    val byteLength: Long,
    val sha256: String,
    val durationMs: Long,
) {
    internal fun isValidFor(storyId: String): Boolean =
        STORY_ID.matches(storyId) && url == "/v1/stories/$storyId/audio" &&
            contentType == "audio/mp4" && byteLength in 1..MAX_AUDIO_BYTES &&
            SHA256.matches(sha256) && durationMs in 1..MAX_DURATION_MS

    private companion object {
        const val MAX_AUDIO_BYTES = 20L * 1024 * 1024
        const val MAX_DURATION_MS = 3_600_000L
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

data class CachedStoryAudio(
    val story: QueueStory,
    val asset: AudioAsset,
    val file: File,
    val verifiedUntilMs: Long = Long.MAX_VALUE,
) {
    internal fun isAvailable() = file.isFile && System.currentTimeMillis() <= verifiedUntilMs
}

/** All operations run on a worker thread. The cache survives app restarts and stays on this device. */
internal class StoryAudioCache(
    private val directory: File,
    baseUrl: String,
    private val maxBytes: Long = MAX_CACHE_BYTES,
) {
    constructor(context: Context, baseUrl: String = BuildConfig.API_BASE_URL) :
        this(File(context.noBackupFilesDir, "story-audio"), baseUrl)

    private val api = URI(baseUrl.trimEnd('/')).normalize().also {
        require(it.scheme in setOf("http", "https") && it.host != null &&
            it.userInfo == null && it.rawQuery == null && it.rawFragment == null)
    }.toASCIIString()
    private val originKey = sha256(api)

    fun fetch(story: QueueStory, asset: AudioAsset): CachedStoryAudio = synchronized(cacheLock) {
        require(asset.isValidFor(story.id) && asset.byteLength <= maxBytes)
        checkNotInterrupted()
        findVerified(story.id, publishedSha = asset.sha256)?.takeIf { it.asset == asset }?.let { cached ->
            val metadata = File(cached.file.parentFile, METADATA_FILE)
            metadata.setLastModified(System.currentTimeMillis())
            return@synchronized cached.copy(story = story, verifiedUntilMs = expiresAt(metadata))
        }

        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Audio storage is unavailable")
        // Clean up work left by a process that stopped during a download.
        directory.listFiles { file -> file.name.startsWith(STAGING_PREFIX) }?.forEach(File::deleteRecursively)
        val target = File(directory, "${storyPrefix(story.id)}${UUID.randomUUID()}")
        val staging = Files.createTempDirectory(directory.toPath(), STAGING_PREFIX).toFile()
        try {
            val stagedAudio = File(staging, AUDIO_FILE)
            val metadata = encodeMetadata(story, asset)
            download(asset, stagedAudio, metadata.size)
            FileOutputStream(File(staging, METADATA_FILE)).use { output ->
                output.write(metadata)
                output.fd.sync()
            }
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            target.setLastModified(System.currentTimeMillis())
            storyDirectories(story.id).filter { it != target }.forEach(File::deleteRecursively)
            CachedStoryAudio(story, asset, File(target, AUDIO_FILE), expiresAt(File(target, METADATA_FILE)))
        } finally {
            staging.deleteRecursively()
        }
    }

    fun find(storyId: String): CachedStoryAudio? = synchronized(cacheLock) { findVerified(storyId) }

    fun has(storyId: String): Boolean = synchronized(cacheLock) {
        storyDirectories(storyId).any { isFresh(it) && readEntry(it, storyId) != null }
    }

    fun remove(storyId: String) {
        synchronized(cacheLock) { storyDirectories(storyId).forEach(File::deleteRecursively) }
    }

    private fun findVerified(storyId: String, publishedSha: String? = null): CachedStoryAudio? {
        var verified: CachedStoryAudio? = null
        for (folder in storyDirectories(storyId).sortedByDescending(File::lastModified)) {
            checkNotInterrupted()
            val entry = readEntry(folder, storyId)
            if (entry == null) {
                folder.deleteRecursively()
                continue
            }
            if (publishedSha != null && entry.asset.sha256 != publishedSha) {
                // Invalidate the old revision before trying to download its replacement.
                val metadata = File(folder, METADATA_FILE)
                if (!metadata.delete() && metadata.exists()) throw IOException("Audio revision could not be invalidated")
                folder.deleteRecursively()
                continue
            }
            if (verified != null || (publishedSha == null && !isFresh(folder))) continue
            val valid = runCatching { digest(entry.file) == entry.asset.sha256 }.getOrDefault(false)
            checkNotInterrupted()
            if (valid) {
                folder.setLastModified(System.currentTimeMillis())
                if (publishedSha == null) return entry
                verified = entry
            } else {
                folder.deleteRecursively()
            }
        }
        return verified
    }

    private fun isFresh(folder: File): Boolean =
        System.currentTimeMillis() - File(folder, METADATA_FILE).lastModified() in 0..MAX_OFFLINE_AGE_MS

    private fun expiresAt(metadata: File) = metadata.lastModified() + MAX_OFFLINE_AGE_MS

    private fun download(asset: AudioAsset, target: File, metadataBytes: Int) {
        val connection = URI("$api${asset.url}").toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 3_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", asset.contentType)
            connection.setRequestProperty("Accept-Encoding", "identity")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("Story audio is unavailable")
            val length = connection.contentLengthLong
            if (!asset.contentType.equals(connection.contentType?.substringBefore(';')?.trim(), ignoreCase = true) ||
                (length >= 0 && length != asset.byteLength)) throw IOException("Unexpected audio response")

            reserveSpace(asset.byteLength + metadataBytes)
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    var size = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkNotInterrupted()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > asset.byteLength) throw IOException("Audio file exceeds its declared length")
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                    if (size != asset.byteLength || digest.digest().hex() != asset.sha256) {
                        throw IOException("Audio integrity verification failed")
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun reserveSpace(bytes: Long) {
        if (bytes > maxBytes) throw IOException("Audio exceeds cache capacity")
        val entries = cacheDirectories().sortedBy(File::lastModified)
        var size = entries.sumOf(::entrySize)
        for (entry in entries) {
            if (size <= maxBytes - bytes) break
            val removedBytes = entrySize(entry)
            if (entry.deleteRecursively()) size -= removedBytes
        }
        if (size > maxBytes - bytes) throw IOException("Audio cache is full")
    }

    private fun entrySize(folder: File) = File(folder, AUDIO_FILE).length() + File(folder, METADATA_FILE).length()

    private fun cacheDirectories(): List<File> =
        directory.listFiles { file -> file.isDirectory && CACHE_DIRECTORY.matches(file.name) }?.toList().orEmpty()

    private fun storyDirectories(storyId: String): List<File> {
        if (!STORY_ID.matches(storyId)) return emptyList()
        val prefix = storyPrefix(storyId)
        return cacheDirectories().filter { it.name.startsWith(prefix) }
    }

    private fun storyPrefix(storyId: String) = "$originKey-${sha256(storyId)}-"

    private fun readEntry(folder: File, storyId: String): CachedStoryAudio? = runCatching {
        val metadata = File(folder, METADATA_FILE)
        require(metadata.length() in 1..MAX_METADATA_BYTES)
        val record = JSONObject(metadata.readText())
        require(record.getInt("version") == 1 && record.getString("storyId") == storyId)
        val asset = AudioAsset(record.getString("url"), record.getString("contentType"), record.getLong("byteLength"),
            record.getString("sha256"), record.getLong("durationMs"))
        require(asset.isValidFor(storyId))
        val file = File(folder, AUDIO_FILE)
        require(file.isFile && file.length() == asset.byteLength)
        val story = QueueStory(storyId, record.getString("title"), record.getString("placeName"),
            record.getInt("durationSeconds"), (record.opt("category") as? String), record.getBoolean("familyFriendly"))
        CachedStoryAudio(story, asset, file, expiresAt(metadata))
    }.getOrNull()

    private fun encodeMetadata(story: QueueStory, asset: AudioAsset): ByteArray =
        JSONObject()
            .put("version", 1)
            .put("storyId", story.id)
            .put("title", story.title.take(MAX_LABEL_LENGTH))
            .put("placeName", story.placeName.take(MAX_LABEL_LENGTH))
            .put("durationSeconds", story.durationSeconds)
            .put("category", story.category?.take(MAX_LABEL_LENGTH))
            .put("familyFriendly", story.familyFriendly)
            .put("url", asset.url)
            .put("contentType", asset.contentType)
            .put("byteLength", asset.byteLength)
            .put("sha256", asset.sha256)
            .put("durationMs", asset.durationMs)
            .toString().toByteArray(Charsets.UTF_8)

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkNotInterrupted()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().hex()
    }

    private fun checkNotInterrupted() {
        if (Thread.currentThread().isInterrupted) throw IOException("Audio cache operation interrupted")
    }

    private companion object {
        const val MAX_CACHE_BYTES = 50L * 1024 * 1024
        const val MAX_OFFLINE_AGE_MS = 30L * 24 * 60 * 60 * 1_000
        const val MAX_LABEL_LENGTH = 512
        const val MAX_METADATA_BYTES = 16_384L
        const val STAGING_PREFIX = ".pending-"
        const val AUDIO_FILE = "audio.m4a"
        const val METADATA_FILE = "audio.json"
        val CACHE_DIRECTORY = Regex("[0-9a-f]{64}-[0-9a-f]{64}-[0-9a-f-]{36}")
        val cacheLock = Any()

        fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).hex()

        fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    }
}

private val STORY_ID = Regex("[A-Za-z0-9_-]{1,128}")
