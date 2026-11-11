package com.roadstory.story.api;

import com.roadstory.RoadStoryApplication;
import com.roadstory.story.infrastructure.InMemoryStoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RoadStoryApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StoryControllerTest {
    private final MockMvc mockMvc;

    @Autowired
    StoryControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void publishedStory만_주변_조회에_노출한다() throws Exception {
        mockMvc.perform(get("/v1/stories/nearby").param("latitude", "37.544").param("longitude", "127.056"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].id").value("story-published-1"))
            .andExpect(jsonPath("$.items[0].familyFriendly").value(false))
            .andExpect(jsonPath("$.items[0].distanceMeters").value(70))
            .andExpect(jsonPath("$.items[1].id").value("story-published-2"))
            .andExpect(jsonPath("$.items[1].familyFriendly").value(true));
    }

    @Test
    void 먼_위치에서는_가까운_이야기가_없다() throws Exception {
        mockMvc.perform(get("/v1/stories/nearby").param("latitude", "37.5").param("longitude", "127.0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 지정한_반경을_적용한다() throws Exception {
        mockMvc.perform(get("/v1/stories/nearby").param("latitude", "37.544").param("longitude", "127.056")
                .param("radiusMeters", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("story-published-1"));
    }

    @Test
    void 주행할_때에만_후방_이야기의_우선순위를_낮춘다() throws Exception {
        mockMvc.perform(get("/v1/stories/nearby").param("latitude", "37.54515").param("longitude", "127.056")
                .param("speedMps", "8").param("heading", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value("story-published-2"));
        mockMvc.perform(get("/v1/stories/nearby").param("latitude", "37.54515").param("longitude", "127.056")
                .param("speedMps", "0").param("heading", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value("story-published-1"));
    }

    @Test
    void 로컬_큐_샘플은_별도의_Story_상세를_반환한다() throws Exception {
        mockMvc.perform(get("/v1/stories/story-published-2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("story-published-2"))
            .andExpect(jsonPath("$.title").value("두 번째 데모 이야기"))
            .andExpect(jsonPath("$.familyFriendly").value(true))
            .andExpect(jsonPath("$.fullText").isNotEmpty())
            .andExpect(jsonPath("$.verificationStatus").value("PUBLISHED"));
    }

    @Test
    void 로컬_큐_샘플은_다른_프로필에서_노출하지_않는다() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("local");
        environment.setActiveProfiles("production");
        assertTrue(new InMemoryStoryRepository(environment).findById("story-published-2").isEmpty());

        environment.setActiveProfiles("local", "production");
        assertTrue(new InMemoryStoryRepository(environment).findById("story-published-2").isEmpty());
    }

    @Test
    void draftStory_상세는_404이다() throws Exception {
        mockMvc.perform(get("/v1/stories/story-draft-1"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));
    }

    @Test
    void 잘못된_좌표는_원본_좌표를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/v1/stories/nearby").param("latitude", "999").param("longitude", "127.0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.detail").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void 존재하지_않는_Story는_안정적인_오류_코드를_반환한다() throws Exception {
        mockMvc.perform(get("/v1/stories/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));
    }

    @Test
    void Story_상세는_TTS용_검수_완료_Text를_반환한다() throws Exception {
        mockMvc.perform(get("/v1/stories/story-published-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullText").isNotEmpty())
            .andExpect(jsonPath("$.audio").doesNotExist());
    }

    @Test
    void healthCheck가_동작한다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiJson을_제공한다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Road Story API"))
            .andExpect(jsonPath("$.paths['/v1/stories/nearby']").exists());
    }

    @Test
    void swaggerUi_진입점을_제공한다() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
