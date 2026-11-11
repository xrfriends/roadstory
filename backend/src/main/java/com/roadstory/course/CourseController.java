package com.roadstory.course;

import com.roadstory.common.error.ApiException;
import com.roadstory.course.CourseCatalog.CourseDetail;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/courses")
public class CourseController {
    private final CourseCatalog courses;

    public CourseController(CourseCatalog courses) {
        this.courses = courses;
    }

    @GetMapping
    public CourseListResponse list() {
        return new CourseListResponse(courses.published().stream().map(this::toSummary).toList(), null);
    }

    @GetMapping("/{courseId}")
    public CourseDetail detail(@PathVariable String courseId) {
        return courses.findPublished(courseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND",
                "ThemeCourse를 찾을 수 없습니다."));
    }

    private CourseSummary toSummary(CourseDetail detail) {
        return new CourseSummary(detail.id(), detail.title(), detail.description(), detail.durationSeconds(),
            detail.distanceMeters(), detail.stopCount(), detail.storyCount(), detail.familyFriendly(), detail.offlineAvailable());
    }

    public record CourseSummary(String id, String title, String description, int durationSeconds,
                                int distanceMeters, int stopCount, int storyCount,
                                boolean familyFriendly, boolean offlineAvailable) {}

    public record CourseListResponse(List<CourseSummary> items, String nextCursor) {}
}
