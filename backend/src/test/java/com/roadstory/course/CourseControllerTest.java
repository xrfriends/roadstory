package com.roadstory.course;

import com.roadstory.RoadStoryApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RoadStoryApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CourseControllerTest {
    private final MockMvc mvc;

    @Autowired
    CourseControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void 공개_코스_목록과_상세의_정차점과_이야기_수가_일치한다() throws Exception {
        mvc.perform(get("/v1/courses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("course-seongsu"))
            .andExpect(jsonPath("$.items[0].stopCount").value(1))
            .andExpect(jsonPath("$.items[0].storyCount").value(1))
            .andExpect(jsonPath("$.items[0].offlineAvailable").value(false));
        mvc.perform(get("/v1/courses/course-seongsu"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stopCount").value(1))
            .andExpect(jsonPath("$.storyCount").value(1))
            .andExpect(jsonPath("$.durationSeconds").value(60))
            .andExpect(jsonPath("$.stops[0].placeName").value("성수역"))
            .andExpect(jsonPath("$.stops[0].coordinates.latitude").value(37.544628))
            .andExpect(jsonPath("$.stops[0].storyIds[0]").value("story-published-1"))
            .andExpect(jsonPath("$.stops[0].stories[0].id").value("story-published-1"));
    }

    @Test
    void 알려지지_않은_코스_ID는_404이다() throws Exception {
        mvc.perform(get("/v1/courses/unknown-course"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }
}
