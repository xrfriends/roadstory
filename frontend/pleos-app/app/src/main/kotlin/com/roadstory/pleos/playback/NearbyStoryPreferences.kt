package com.roadstory.pleos.playback

import com.roadstory.pleos.user.ListeningFrequency

data class NearbyStoryPreferences(
    val frequencies: Map<String, ListeningFrequency> = emptyMap(),
    val familyMode: Boolean = false,
    val dislikedStoryIds: Set<String> = emptySet(),
    val dislikedCategories: Set<String> = emptySet(),
) {
    internal fun firstEligible(candidates: List<QueueStory>, selectedIds: Set<String>): QueueStory? =
        candidates.asSequence()
            .filter { isEligible(it, selectedIds) }
            .minWithOrNull(
                compareBy<QueueStory>(
                    { familyMode && !it.familyFriendly },
                    { it.category?.let(dislikedCategories::contains) == true },
                    { frequency(it) != ListeningFrequency.OFTEN },
                ),
            )

    internal fun isEligible(story: QueueStory, selectedIds: Set<String>) =
        story.id !in selectedIds && story.id !in dislikedStoryIds && frequency(story) != ListeningFrequency.OFF

    private fun frequency(story: QueueStory) =
        story.category?.let(frequencies::get) ?: ListeningFrequency.SOMETIMES
}
