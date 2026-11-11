package com.roadstory.user;

import com.roadstory.common.error.ApiException;
import com.roadstory.course.CourseCatalog;
import com.roadstory.course.CourseCatalog.CourseDetail;
import com.roadstory.story.application.StoryService;
import com.roadstory.story.domain.Story;
import com.roadstory.user.AccountLibraryStore.Kind;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class UserLibraryService {
    private final AccountLibraryStore store;
    private final StoryService stories;
    private final CourseCatalog courses;

    UserLibraryService(AccountLibraryStore store, StoryService stories, CourseCatalog courses) {
        this.store = store;
        this.stories = stories;
        this.courses = courses;
    }

    UserLibrary library(String accountId) {
        Map<String, Story> publishedStories = stories.published().stream()
            .collect(Collectors.toMap(Story::id, Function.identity()));
        Map<String, CourseDetail> publishedCourses = courses.published().stream()
            .collect(Collectors.toMap(CourseDetail::id, Function.identity()));
        var savedStories = new ArrayList<LibraryItem>();
        var savedCourses = new ArrayList<LibraryItem>();
        var history = new ArrayList<ListeningHistoryItem>();
        for (var entry : store.library(accountId, publishedStories.keySet())) {
            switch (entry.kind()) {
                case STORY -> {
                    Story story = publishedStories.get(entry.id());
                    if (story != null) {
                        savedStories.add(new LibraryItem(story.id(), story.title(), story.placeName(), story.category()));
                    }
                }
                case COURSE -> {
                    CourseDetail course = publishedCourses.get(entry.id());
                    if (course != null) {
                        savedCourses.add(new LibraryItem(course.id(), course.title(), course.description(), null));
                    }
                }
                case HISTORY -> {
                    Story story = publishedStories.get(entry.id());
                    if (story != null) {
                        history.add(new ListeningHistoryItem(story.id(), story.title(), story.placeName(),
                            story.category(), entry.recordedAt()));
                    }
                }
            }
        }
        return new UserLibrary(accountId, List.copyOf(savedStories), List.copyOf(savedCourses), List.copyOf(history));
    }

    void saveStory(String accountId, String storyId) {
        stories.detail(storyId);
        store.save(accountId, Kind.STORY, storyId);
    }

    void saveCourse(String accountId, String courseId) {
        if (courses.findPublished(courseId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "ThemeCourse를 찾을 수 없습니다.");
        }
        store.save(accountId, Kind.COURSE, courseId);
    }

    void removeStory(String accountId, String storyId) {
        store.remove(accountId, Kind.STORY, storyId);
    }

    void removeCourse(String accountId, String courseId) {
        store.remove(accountId, Kind.COURSE, courseId);
    }

    void recordListening(String accountId, String storyId, Instant playedAt) {
        if (playedAt.isBefore(Instant.EPOCH) || playedAt.isAfter(Instant.now().plus(5, ChronoUnit.MINUTES))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "청취 시각이 올바르지 않습니다.");
        }
        stories.detail(storyId);
        store.recordListening(accountId, storyId, playedAt);
    }

    UserPreferences preferences(String accountId) {
        return store.preferences(accountId).orElse(UserPreferences.DEFAULT);
    }

    UserPreferences updatePreferences(String accountId, UserPreferences preferences) {
        store.setPreferences(accountId, preferences);
        return preferences;
    }

    public record LibraryItem(String id, String title, String subtitle, String category) {}

    public record ListeningHistoryItem(String id, String title, String subtitle, String category, Instant playedAt) {}

    public record UserLibrary(String accountId, List<LibraryItem> savedStories, List<LibraryItem> savedCourses,
                              List<ListeningHistoryItem> history) {}
}
