package com.roadstory.pleos.playback

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class StoryAudioCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var server: TestHttpServer
    private lateinit var api: String
    private val story = QueueStory("story-a", "공개 이야기", "성수동", 60, "PLACE_NAME", true)
    private val audio = "verified audio".toByteArray()

    @Before
    fun startServer() {
        server = TestHttpServer()
        api = "http://127.0.0.1:${server.port}"
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun `verified download survives cache reopening and stays isolated from other configured origins`() {
        val asset = serve(story, audio)
        val stored = StoryAudioCache(temporary.root, api).fetch(story, asset)
        assertArrayEquals(audio, stored.file.readBytes())

        server.responses.remove(asset.url)
        val reopened = StoryAudioCache(temporary.root, api)
        assertTrue(reopened.has(story.id))
        assertEquals(stored, reopened.find(story.id))
        assertNull(StoryAudioCache(temporary.root, "https://other-api.example").find(story.id))

        stored.file.writeBytes(ByteArray(audio.size))
        assertNull(reopened.find(story.id))
        assertFalse(reopened.has(story.id))
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a failed new revision download keeps obsolete audio unavailable after reopening the cache`() {
        val original = serve(story, audio)
        val cache = StoryAudioCache(temporary.root, api)
        cache.fetch(story, original)
        val otherStory = story.copy(id = "story-b")
        val other = cache.fetch(otherStory, serve(otherStory, audio))
        val next = asset(story, "different data".toByteArray())

        assertThrows(IOException::class.java) { cache.fetch(story, next) }
        assertNull(StoryAudioCache(temporary.root, api).find(story.id))
        assertEquals(other, cache.find(otherStory.id))
        assertEquals(listOf(other.file.parentFile), temporary.root.listFiles()?.toList())
    }

    @Test
    fun `an incomplete same revision transfer keeps the verified existing file`() {
        val original = serve(story, audio)
        val cache = StoryAudioCache(temporary.root, api)
        val stored = cache.fetch(story, original)

        server.responses[original.url] = { Response.audio(audio.copyOf(2), declareLength = false) }
        assertThrows(IOException::class.java) { cache.fetch(story, original.copy(durationMs = 60_001)) }
        assertEquals(stored, cache.find(story.id))
        assertEquals(listOf(stored.file.parentFile), temporary.root.listFiles()?.toList())
    }

    @Test
    fun `offline verification expires separately from recent use and renews against the published descriptor`() {
        val asset = serve(story, audio)
        val cache = StoryAudioCache(temporary.root, api)
        val stored = cache.fetch(story, asset)
        val directory = stored.file.parentFile!!
        val expired = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1_000
        assertTrue(directory.resolve("audio.json").setLastModified(expired))
        assertTrue(directory.setLastModified(System.currentTimeMillis()))

        assertFalse(cache.has(story.id))
        assertNull(cache.find(story.id))
        assertTrue(stored.file.isFile)

        server.responses.remove(asset.url)
        val renewed = cache.fetch(story, asset)
        assertEquals(stored.file, renewed.file)
        assertEquals(stored.asset, renewed.asset)
        assertTrue(renewed.verifiedUntilMs > System.currentTimeMillis())
        assertTrue(cache.has(story.id))
        assertEquals(renewed, cache.find(story.id))
    }

    @Test
    fun `cache rejects descriptor URLs outside the Story route and does not follow redirects`() {
        val asset = asset(story, audio)
        val unexpectedRequests = AtomicInteger()
        server.responses[asset.url] = { Response("302 Found", headers = mapOf("Location" to "$api/unexpected")) }
        server.responses["/unexpected"] = {
            unexpectedRequests.incrementAndGet()
            Response("500 Internal Server Error")
        }
        val cache = StoryAudioCache(temporary.root, api)

        assertThrows(IllegalArgumentException::class.java) { cache.fetch(story, asset.copy(url = "$api/unexpected")) }
        assertThrows(IOException::class.java) { cache.fetch(story, asset) }
        assertEquals(0, unexpectedRequests.get())
        assertFalse(cache.has(story.id))
    }

    @Test
    fun `cache evicts least recently used audio before it exceeds its byte limit`() {
        val otherStory = story.copy(id = "story-b")
        val newStory = story.copy(id = "story-c")
        val fixture = ByteArray(512)
        val asset = serve(story, fixture)
        val otherAsset = serve(otherStory, fixture)
        val newAsset = serve(newStory, fixture)
        val cache = StoryAudioCache(temporary.root, api, maxBytes = 1_800)
        val oldest = cache.fetch(story, asset)
        cache.fetch(otherStory, otherAsset)
        assertTrue(oldest.file.parentFile!!.setLastModified(1_000))

        cache.fetch(newStory, newAsset)

        assertFalse(cache.has(story.id))
        assertTrue(cache.has(otherStory.id))
        assertTrue(cache.has(newStory.id))
    }

    private fun serve(story: QueueStory, bytes: ByteArray): AudioAsset = asset(story, bytes).also { asset ->
        server.responses[asset.url] = { Response.audio(bytes) }
    }

    private fun asset(story: QueueStory, bytes: ByteArray) = AudioAsset(
        url = "/v1/stories/${story.id}/audio",
        contentType = "audio/mp4",
        byteLength = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        durationMs = 60_000,
    )

    private data class Response(
        val status: String,
        val body: ByteArray = byteArrayOf(),
        val headers: Map<String, String> = emptyMap(),
        val declareLength: Boolean = true,
    ) {
        companion object {
            fun audio(bytes: ByteArray, declareLength: Boolean = true) =
                Response("200 OK", bytes, mapOf("Content-Type" to "audio/mp4"), declareLength)
        }
    }

    private class TestHttpServer : AutoCloseable {
        val responses = ConcurrentHashMap<String, () -> Response>()
        private val listener = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = listener.localPort
        private val worker = Thread(::serve).apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!listener.isClosed) {
                val connection = try {
                    listener.accept()
                } catch (failure: SocketException) {
                    if (listener.isClosed) return
                    throw failure
                }
                try {
                    connection.use(::reply)
                } catch (_: IOException) {
                    // Clients can disconnect after reading the headers.
                }
            }
        }

        private fun reply(connection: Socket) {
            connection.soTimeout = 3_000
            val reader = connection.getInputStream().bufferedReader(Charsets.US_ASCII)
            val path = reader.readLine()?.split(' ', limit = 3)?.getOrNull(1) ?: return
            while (!reader.readLine().isNullOrEmpty()) Unit
            val response = responses[path]?.invoke() ?: Response("404 Not Found")
            val headers = buildString {
                append("HTTP/1.1 ${response.status}\r\nConnection: close\r\n")
                if (response.declareLength) append("Content-Length: ${response.body.size}\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("\r\n")
            }
            val output = connection.getOutputStream()
            output.write(headers.toByteArray(Charsets.US_ASCII))
            output.write(response.body)
            output.flush()
        }

        override fun close() {
            listener.close()
            worker.join(1_000)
        }
    }
}
