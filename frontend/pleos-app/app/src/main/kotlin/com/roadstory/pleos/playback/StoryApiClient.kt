package com.roadstory.pleos.playback

import com.roadstory.pleos.BuildConfig
import com.roadstory.pleos.platform.NavigationDestination
import com.roadstory.pleos.platform.VehicleLocationFix
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.round

data class QueueStory(
    val id: String,
    val title: String,
    val placeName: String = "",
    val durationSeconds: Int = 0,
    val category: String? = null,
    val familyFriendly: Boolean = false,
)

data class StorySpeech(
    val id: String,
    val title: String,
    val text: String,
    val placeName: String = "",
    val durationSeconds: Int = 0,
    val category: String? = null,
    val familyFriendly: Boolean = false,
    val audio: AudioAsset? = null,
    val localAudio: CachedStoryAudio? = null,
    val offlineAudio: Boolean = false,
) {
    fun toQueueStory() = QueueStory(id, title, placeName, durationSeconds, category, familyFriendly)
}

internal class StoryApiResponseFailure(val status: Int) : RuntimeException()

class StoryApiClient(private val baseUrl: String = BuildConfig.API_BASE_URL) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()

    fun load(storyId: String, callback: (Result<StorySpeech>) -> Unit) {
        loadJson("/v1/stories/${encoded(storyId)}", ::parseStorySpeech, callback)
    }

    fun loadNearby(fix: VehicleLocationFix, callback: (Result<List<QueueStory>>) -> Unit) {
        loadJson(nearbyPath(fix), ::parseNearbyStories, callback)
    }

    fun loadDestination(
        destination: NavigationDestination,
        mode: DestinationMode,
        callback: (Result<DestinationPlan>) -> Unit,
    ) {
        val seconds = destination.remainingDurationSeconds
        if (seconds == null || seconds < 60) {
            callback(Result.failure(IllegalStateException("안내 남은 시간이 부족합니다.")))
            return
        }
        val body = JSONObject()
            .put("destination", JSONObject().put("latitude", destination.latitude).put("longitude", destination.longitude))
            .put("remainingDurationSeconds", seconds)
            .put("mode", mode.name)
        loadJson("/v1/journeys/destination-playlist", ::parseDestination, callback, body)
    }

    fun loadCourses(callback: (Result<List<CourseSummary>>) -> Unit) =
        loadJson("/v1/courses", ::parseCourses, callback)

    fun loadCourse(courseId: String, callback: (Result<CourseDetail>) -> Unit) =
        loadJson("/v1/courses/${encoded(courseId)}", ::parseCourse, callback)

    private fun <T> loadJson(path: String, parse: (String) -> T, callback: (Result<T>) -> Unit, body: JSONObject? = null) {
        executor.execute {
            callback(runCatching {
                val connection = URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.connectTimeout = 3_000
                    connection.readTimeout = 5_000
                    connection.setRequestProperty("Accept", "application/json")
                    if (body != null) {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    }
                    val status = connection.responseCode
                    if (status != HttpURLConnection.HTTP_OK) throw StoryApiResponseFailure(status)
                    parse(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            })
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    companion object {
        private fun encoded(id: String) = URLEncoder.encode(id, Charsets.UTF_8.name()).replace("+", "%20")

        internal fun nearbyPath(fix: VehicleLocationFix): String = buildString {
            // The server only needs neighborhood precision; never log the URL.
            fun reduced(value: Double) = round(value * 10_000) / 10_000
            append("/v1/stories/nearby?latitude=${reduced(fix.latitude)}&longitude=${reduced(fix.longitude)}")
            fix.speedMps?.takeIf { it.isFinite() && it >= 0.0 }?.let { speed ->
                append("&speedMps=$speed")
                if (speed >= 2.0) fix.heading?.takeIf { it in 0.0..360.0 }?.let { append("&heading=$it") }
            }
        }

        fun parseStorySpeech(json: String): StorySpeech {
            val root = JSONObject(json)
            val id = root.getString("id")
            val text = root.getString("fullText").trim()
            require(text.isNotEmpty()) { "TTS 낭독문이 없습니다." }
            val audio = root.optJSONObject("audio")?.let { value ->
                AudioAsset(value.getString("url"), value.getString("contentType"), value.getLong("byteLength"),
                    value.getString("sha256"), value.getLong("durationMs"))
                    .also { require(it.isValidFor(id)) { "이야기 음원 정보가 올바르지 않습니다." } }
            }
            return StorySpeech(
                id,
                root.getString("title"),
                text,
                root.optString("placeName"),
                root.optInt("durationSeconds").coerceAtLeast(0),
                root.storyCategory(),
                root.opt("familyFriendly") == true,
                audio = audio,
            )
        }

        fun parseNearbyStories(json: String): List<QueueStory> =
            JSONObject(json).getJSONArray("items").objects(::summary)

        fun parseDestination(json: String): DestinationPlan = JSONObject(json).let {
            DestinationPlan(
                PlaylistBasis.valueOf(it.getString("basis")),
                it.getInt("totalAudioDurationSeconds").coerceAtLeast(0),
                it.getJSONArray("items").objects(::summary),
            )
        }

        fun parseCourses(json: String): List<CourseSummary> =
            JSONObject(json).getJSONArray("items").objects(::courseSummary)

        fun parseCourse(json: String): CourseDetail = JSONObject(json).let { root ->
            CourseDetail(courseSummary(root), root.getJSONArray("stops").objects { stop ->
                val coordinates = stop.getJSONObject("coordinates")
                val latitude = coordinates.getDouble("latitude")
                val longitude = coordinates.getDouble("longitude")
                require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
                CourseStop(
                    stop.getInt("sequence"),
                    stop.getString("placeName"),
                    latitude,
                    longitude,
                    stop.optJSONArray("stories")?.objects(::summary).orEmpty(),
                )
            }.sortedBy(CourseStop::sequence))
        }

        private fun summary(item: JSONObject) = QueueStory(
            item.getString("id"), item.getString("title"), item.optString("placeName"),
            item.optInt("durationSeconds").coerceAtLeast(0), item.storyCategory(),
            item.opt("familyFriendly") == true,
        )

        private fun courseSummary(item: JSONObject) = CourseSummary(
            item.getString("id"), item.getString("title"), item.optString("description"),
            item.getInt("durationSeconds"), item.getInt("distanceMeters"), item.getInt("stopCount"),
            item.getInt("storyCount"), item.opt("familyFriendly") == true, item.opt("offlineAvailable") == true,
        )

        private fun <T> JSONArray.objects(parse: (JSONObject) -> T): List<T> =
            List(length()) { parse(getJSONObject(it)) }

        private fun JSONObject.storyCategory(): String? =
            (opt("category") as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
