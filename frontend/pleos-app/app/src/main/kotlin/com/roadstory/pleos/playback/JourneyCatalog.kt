package com.roadstory.pleos.playback

enum class DestinationMode(val label: String) { SHORT("짧게 듣기"), STANDARD("기본"), FAMILY("가족과") }
enum class PlaylistBasis { DESTINATION_NEARBY, ROUTE }

data class DestinationPlan(
    val basis: PlaylistBasis,
    val totalAudioDurationSeconds: Int,
    val stories: List<QueueStory>,
)

data class CourseSummary(
    val id: String,
    val title: String,
    val description: String,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val stopCount: Int,
    val storyCount: Int,
    val familyFriendly: Boolean,
    val offlineAvailable: Boolean,
)

data class CourseStop(
    val sequence: Int,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val stories: List<QueueStory>,
)

data class CourseDetail(val summary: CourseSummary, val stops: List<CourseStop>) {
    val stories: List<QueueStory> get() = stops.flatMap(CourseStop::stories).distinctBy(QueueStory::id)
}
