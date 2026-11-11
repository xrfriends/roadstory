package com.roadstory.course;

import com.roadstory.story.api.StorySummaryResponse;
import com.roadstory.story.application.StoryService;
import com.roadstory.story.infrastructure.PublicStoryLocations;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CourseCatalog {
    private static final String SEONGSU_ID = "course-seongsu";

    private final StoryService stories;

    public CourseCatalog(StoryService stories) {
        this.stories = stories;
    }

    public List<CourseDetail> published() {
        return findPublished(SEONGSU_ID).stream().toList();
    }

    public Optional<CourseDetail> findPublished(String courseId) {
        if (!SEONGSU_ID.equals(courseId)) {
            return Optional.empty();
        }
        return stories.findPublished("story-published-1").map(story -> {
            var station = PublicStoryLocations.SEONGSU_STATION;
            var stop = new CourseStop(1, "성수역", new Coordinates(station.latitude(), station.longitude()),
                List.of(story.id()), List.of(StorySummaryResponse.from(story, 0)));
            var stops = List.of(stop);
            return new CourseDetail(SEONGSU_ID, "성수 산업유산 드라이브", "성수역 주변 수제화 거리 이야기를 듣습니다.",
                story.durationSeconds(), 0, stops.size(), stop.storyIds().size(), story.familyFriendly(), false, stops);
        });
    }

    public record CourseStop(int sequence, String placeName, Coordinates coordinates, List<String> storyIds,
                             List<StorySummaryResponse> stories) {}

    public record Coordinates(double latitude, double longitude) {}

    public record CourseDetail(String id, String title, String description, int durationSeconds,
                               int distanceMeters, int stopCount, int storyCount,
                               boolean familyFriendly, boolean offlineAvailable, List<CourseStop> stops) {}
}
