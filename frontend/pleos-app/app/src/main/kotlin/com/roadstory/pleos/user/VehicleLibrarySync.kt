package com.roadstory.pleos.user

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.roadstory.pleos.BuildConfig
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/** Shared between the activity and playback service. Development credentials only live in this process. */
class VehicleLibrarySync private constructor(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArrayList<(LibrarySyncStatus) -> Unit>()
    private val ownerScopes = ConcurrentHashMap<String, String>()
    private val guest = context.getSharedPreferences(VEHICLE_GUEST_NAMESPACE, Context.MODE_PRIVATE)
    private val coordinator = LibrarySyncCoordinator(
        transport = HttpLibraryTransport(BuildConfig.API_BASE_URL),
        persistence = PreferenceLibraryPersistence(context),
        execute = { task -> network.execute(task) },
        dispatch = ::onMain,
        schedule = { millis, task -> main.postDelayed(task, millis) },
        changed = { onMain { listeners.forEach { it(status) } } },
    )

    val developmentSignInAvailable: Boolean =
        BuildConfig.DEBUG && BuildConfig.DEV_LIBRARY_SYNC && isLocalApi(BuildConfig.API_BASE_URL)

    val status: LibrarySyncStatus get() = coordinator.status()
    val guestSavedStories: List<LibraryItem>
        get() = LibraryJson.readStoredItems(guest.getString("savedStories", null))
    val guestSavedCourses: List<LibraryItem>
        get() = LibraryJson.readStoredItems(guest.getString("savedCourses", null))

    fun observe(listener: (LibrarySyncStatus) -> Unit): AutoCloseable {
        listeners.add(listener)
        onMain { if (listeners.contains(listener)) listener(status) }
        return AutoCloseable { listeners.remove(listener) }
    }

    fun signInForDevelopment(token: String) {
        if (developmentSignInAvailable) onMain { coordinator.signIn(token.trim()) }
    }

    fun signOut() = onMain(coordinator::signOut)

    fun syncNow() = onMain(coordinator::syncNow)

    fun importGuestBookmarks(expectedAccountId: String, storyIds: Set<String>, courseIds: Set<String>): Int =
        coordinator.importBookmarks(
            expectedAccountId,
            guestSavedStories.filter { it.id in storyIds },
            guestSavedCourses.filter { it.id in courseIds },
        )

    internal fun visibleLibrary() = coordinator.visibleLibrary()

    internal fun currentStory() = coordinator.currentStory()

    internal fun setCurrentStory(item: LibraryItem) = coordinator.setCurrentStory(item)

    internal fun toggle(kind: LibraryKind, item: LibraryItem) = coordinator.toggle(kind, item)

    internal fun recordListening(item: LibraryItem) = coordinator.recordListening(item)

    internal fun accountPreferencePrefix(): String? =
        status.accountId?.let { accountId -> ownerScopes.computeIfAbsent(accountId) { "account.${ownerKey(it)}." } }

    private fun onMain(task: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) task() else main.post(task)
    }

    companion object {
        @Volatile private var instance: VehicleLibrarySync? = null

        fun get(context: Context): VehicleLibrarySync = instance ?: synchronized(this) {
            instance ?: VehicleLibrarySync(context.applicationContext).also { instance = it }
        }
    }
}

internal fun isLocalApi(baseUrl: String): Boolean = runCatching {
    val uri = URI(baseUrl)
    uri.scheme in setOf("http", "https") &&
        uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost") &&
        uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
        uri.path.orEmpty().trimEnd('/').isEmpty()
}.getOrDefault(false)

internal fun ownerKey(accountId: String): String =
    MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private class PreferenceLibraryPersistence(context: Context) : LibraryPersistence {
    private val preferences = context.getSharedPreferences("road_story_account_library", Context.MODE_PRIVATE)

    override fun load(accountId: String): AccountLibrary {
        val stored = preferences.getString(ownerKey(accountId), null) ?: return AccountLibrary()
        return runCatching { LibraryJson.readCache(JSONObject(stored)) }.getOrDefault(AccountLibrary())
    }

    override fun save(accountId: String, library: AccountLibrary) {
        // The snapshot and pending operations must be replaced together.
        preferences.edit().putString(ownerKey(accountId), LibraryJson.writeCache(library).toString()).apply()
    }
}

private class HttpLibraryTransport(private val baseUrl: String) : LibraryTransport {
    override fun fetch(bearer: String): VerifiedLibrary =
        request(bearer, "GET", "/v1/users/me/library", HttpURLConnection.HTTP_OK) { connection ->
            val root = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val accountId = root.getString("accountId")
            require(accountId.isNotBlank() && accountId.length <= 256)
            VerifiedLibrary(accountId, LibraryJson.readContents(root))
        }

    override fun write(bearer: String, mutation: LibraryMutation) {
        val encodedId = URLEncoder.encode(mutation.item.id, Charsets.UTF_8.name()).replace("+", "%20")
        val path = when (mutation.kind) {
            LibraryKind.STORY -> "/v1/users/me/saved-stories/$encodedId"
            LibraryKind.COURSE -> "/v1/users/me/saved-courses/$encodedId"
            LibraryKind.HISTORY -> "/v1/users/me/listening-history/$encodedId"
        }
        val method = if (mutation.saved) "PUT" else "DELETE"
        val body = mutation.playedAt?.let { JSONObject().put("playedAt", it.toString()) }
        request(bearer, method, path, HttpURLConnection.HTTP_NO_CONTENT, body) { }
    }

    private fun <T> request(
        bearer: String,
        method: String,
        path: String,
        successCode: Int,
        body: JSONObject? = null,
        read: (HttpURLConnection) -> T,
    ): T {
        val connection = URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            return when (connection.responseCode) {
                successCode -> read(connection)
                401, 403 -> throw LibraryAuthenticationRequired()
                400, 404, 422 -> if (method != "GET") throw LibraryMutationRejected() else error("Library unavailable")
                else -> error("Library unavailable")
            }
        } finally {
            connection.disconnect()
        }
    }
}

internal object LibraryJson {
    fun readStoredItems(text: String?): List<LibraryItem> = runCatching {
        JSONArray(text ?: "[]").mapObjects(::readItem)
    }.getOrDefault(emptyList())

    fun readContents(root: JSONObject): LibraryContents {
        val history = root.getJSONArray("history").mapObjects {
            ListeningRecord(readItem(it), Instant.parse(it.getString("playedAt")))
        }
        return LibraryContents(
            savedStories = root.getJSONArray("savedStories").mapObjects(::readItem),
            savedCourses = root.getJSONArray("savedCourses").mapObjects(::readItem),
            history = history.sortedByDescending { it.playedAt }.take(100),
        )
    }

    fun writeCache(library: AccountLibrary): JSONObject = writeContents(library.snapshot)
        .put("currentStory", library.currentStory?.let(::writeItem))
        .put("pending", JSONArray().apply {
            library.pending.forEach {
                put(JSONObject()
                    .put("kind", it.kind.name)
                    .put("item", writeItem(it.item))
                    .put("saved", it.saved)
                    .put("playedAt", it.playedAt?.toString())
                    .put("revision", it.revision))
            }
        })

    fun readCache(root: JSONObject): AccountLibrary = AccountLibrary(
        snapshot = readContents(root),
        pending = root.getJSONArray("pending").mapObjects {
            LibraryMutation(
                kind = LibraryKind.valueOf(it.getString("kind")),
                item = readItem(it.getJSONObject("item")),
                saved = it.getBoolean("saved"),
                playedAt = (it.opt("playedAt") as? String)?.let(Instant::parse),
                revision = it.getString("revision"),
            )
        },
        currentStory = root.optJSONObject("currentStory")?.let(::readItem),
    )

    fun readItem(item: JSONObject) = LibraryItem(
        item.getString("id"),
        item.getString("title"),
        item.optString("subtitle"),
        item.opt("category") as? String,
    )

    fun writeItem(item: LibraryItem): JSONObject =
        JSONObject().put("id", item.id).put("title", item.title)
            .put("subtitle", item.subtitle).put("category", item.category)

    private fun writeContents(contents: LibraryContents) = JSONObject()
        .put("savedStories", JSONArray().apply { contents.savedStories.forEach { put(writeItem(it)) } })
        .put("savedCourses", JSONArray().apply { contents.savedCourses.forEach { put(writeItem(it)) } })
        .put("history", JSONArray().apply {
            contents.history.forEach { put(writeItem(it.item).put("playedAt", it.playedAt.toString())) }
        })

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        List(length()) { transform(getJSONObject(it)) }
}
