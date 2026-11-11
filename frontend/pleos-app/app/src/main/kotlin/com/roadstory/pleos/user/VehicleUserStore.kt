package com.roadstory.pleos.user

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class LibraryItem(val id: String, val title: String, val subtitle: String = "", val category: String? = null)
enum class ListeningFrequency(val label: String) { OFTEN("자주"), SOMETIMES("가끔"), OFF("끄기") }
data class InterestTopic(val key: String, val label: String, val default: ListeningFrequency = ListeningFrequency.SOMETIMES)
internal const val VEHICLE_GUEST_NAMESPACE = "road_story_user"

/** The default store presents the verified account library or the existing guest data. */
class VehicleUserStore(context: Context, namespace: String = VEHICLE_GUEST_NAMESPACE) {
    private val preferences = context.applicationContext.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val sync by lazy {
        if (namespace == VEHICLE_GUEST_NAMESPACE) VehicleLibrarySync.get(context) else null
    }

    var autoplay: Boolean
        get() = preferences.getBoolean(preferenceKey("autoplay"), true)
        set(value) { preferences.edit().putBoolean(preferenceKey("autoplay"), value).apply() }
    var familyMode: Boolean
        get() = preferences.getBoolean(preferenceKey("familyMode"), false)
        set(value) { preferences.edit().putBoolean(preferenceKey("familyMode"), value).apply() }

    fun frequency(topic: InterestTopic): ListeningFrequency = runCatching {
        ListeningFrequency.valueOf(preferences.getString(preferenceKey("topic.${topic.key}"), topic.default.name)!!)
    }.getOrDefault(topic.default)

    fun setFrequency(topic: InterestTopic, value: ListeningFrequency) {
        preferences.edit().putString(preferenceKey("topic.${topic.key}"), value.name).apply()
    }

    fun observe(changed: () -> Unit): AutoCloseable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changed() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        val accountObserver = sync?.observe { changed() }
        return AutoCloseable {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            accountObserver?.close()
        }
    }

    val savedStories: List<LibraryItem> get() = sync?.visibleLibrary()?.savedStories ?: readItems("savedStories")
    val savedCourses: List<LibraryItem> get() = sync?.visibleLibrary()?.savedCourses ?: readItems("savedCourses")
    val history: List<LibraryItem> get() =
        sync?.visibleLibrary()?.history?.map(ListeningRecord::item) ?: readItems("history")
    var currentStory: LibraryItem
        get() {
            val librarySync = sync
            return if (librarySync?.status?.accountId != null) {
                librarySync.currentStory() ?: DEFAULT_STORY
            } else {
                readItems("currentStory").firstOrNull() ?: DEFAULT_STORY
            }
        }
        set(value) {
            if (sync?.setCurrentStory(value) != true) writeItems("currentStory", listOf(value))
        }

    fun isStorySaved(id: String) = savedStories.any { it.id == id }
    fun isCourseSaved(id: String) = savedCourses.any { it.id == id }
    fun toggleStory(item: LibraryItem) = sync?.toggle(LibraryKind.STORY, item) ?: toggle("savedStories", item)
    fun toggleCourse(item: LibraryItem) = sync?.toggle(LibraryKind.COURSE, item) ?: toggle("savedCourses", item)
    val dislikedStoryIds: Set<String>
        get() = preferences.getStringSet(preferenceKey("dislikedStoryIds"), emptySet()).orEmpty().toSet()
    val dislikedCategories: Set<String>
        get() {
            val categories = readDislikedCategories()
            return dislikedStoryIds.mapNotNull { categories.opt(it) as? String }.toSet()
        }

    fun isStoryDisliked(id: String) = id in dislikedStoryIds
    fun dislikedStoryCategory(id: String): String? = readDislikedCategories().opt(id) as? String

    fun setStoryDisliked(item: LibraryItem, disliked: Boolean) = setStoryDisliked(item.id, disliked, item.category)

    fun setStoryDisliked(id: String, disliked: Boolean, category: String? = null) {
        val ids = dislikedStoryIds
        val categories = readDislikedCategories()
        val newCategory = if (disliked) category?.trim()?.takeIf { it.isNotEmpty() } else null
        val previousCategory = categories.opt(id) as? String
        val updatedCategory = if (disliked) newCategory ?: previousCategory else null
        if (disliked == (id in ids) && updatedCategory == previousCategory) return

        if (updatedCategory == null) categories.remove(id) else categories.put(id, updatedCategory)
        preferences.edit()
            .putStringSet(preferenceKey("dislikedStoryIds"), if (disliked) ids + id else ids - id)
            .putString(preferenceKey("dislikedStoryCategories"), categories.toString())
            .apply()
    }

    fun recordListening(item: LibraryItem) {
        if (sync?.recordListening(item) == true) return
        writeItems("currentStory", listOf(item))
        writeItems("history", (listOf(item) + readItems("history").filterNot { it.id == item.id }).take(100))
        val saved = readItems("savedStories")
        if (saved.any { it.id == item.id }) writeItems("savedStories", saved.map { if (it.id == item.id) item else it })
    }

    private fun toggle(key: String, item: LibraryItem): Boolean {
        val items = readItems(key)
        val saving = items.none { it.id == item.id }
        writeItems(key, if (saving) items + item else items.filterNot { it.id == item.id })
        return saving
    }

    private fun readItems(key: String): List<LibraryItem> = LibraryJson.readStoredItems(preferences.getString(key, null))

    private fun writeItems(key: String, items: List<LibraryItem>) {
        val array = JSONArray()
        items.forEach { array.put(LibraryJson.writeItem(it)) }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun readDislikedCategories(): JSONObject = runCatching {
        JSONObject(preferences.getString(preferenceKey("dislikedStoryCategories"), null) ?: "{}")
    }.getOrElse { JSONObject() }

    private fun preferenceKey(key: String): String = sync?.accountPreferencePrefix()?.plus(key) ?: key

    companion object {
        val DEFAULT_STORY = LibraryItem("story-published-1", "성수동, 이름에 담긴 두 개의 이야기", "성수동")
        val DEFAULT_COURSE = LibraryItem("course-seongsu", "서울의 옛 공장, 새로운 풍경", "1시간 20분 · 이야기 4개")
        val topics = listOf(
            InterestTopic("PLACE_NAME", "지명 유래", ListeningFrequency.OFTEN),
            InterestTopic("HISTORY", "역사적 사건"), InterestTopic("LANDMARK", "랜드마크"),
            InterestTopic("ARCHITECTURE", "건축", ListeningFrequency.OFTEN),
            InterestTopic("PERSON", "인물"), InterestTopic("DAILY_LIFE", "생활사"),
            InterestTopic("MARKET_COMMERCE", "시장과 상권"), InterestTopic("INDUSTRY", "산업화"),
            InterestTopic("NATURE_GEOGRAPHY", "자연과 지형"), InterestTopic("CHILDREN", "어린이 이야기"),
            InterestTopic("FILM_LITERATURE", "영화와 문화"),
        )
    }
}
