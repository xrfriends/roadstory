package com.roadstory.story.api;

import com.roadstory.story.domain.Story;

public record StorySummaryResponse(String id, String title, String placeName, String category,
                                   boolean familyFriendly, int distanceMeters, int durationSeconds, String hook,
                                   boolean saved) {
    public static StorySummaryResponse from(Story story, double distanceMeters) {
        return new StorySummaryResponse(story.id(), story.title(), story.placeName(), story.category(),
            story.familyFriendly(), (int) Math.round(distanceMeters), story.durationSeconds(), story.hook(), false);
    }
}
