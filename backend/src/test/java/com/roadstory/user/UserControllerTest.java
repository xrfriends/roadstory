package com.roadstory.user;

import com.roadstory.RoadStoryApplication;
import java.time.Instant;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RoadStoryApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UserControllerTest {
    private static final String ROOT = "/v1/users/me";
    private static final String LIBRARY = ROOT + "/library";
    private static final String PREFERENCES = ROOT + "/preferences";
    private static final String STORY = ROOT + "/saved-stories/story-published-1";
    private static final String COURSE = ROOT + "/saved-courses/course-seongsu";
    private static final String HISTORY = ROOT + "/listening-history/";

    private final MockMvc mvc;
    private final AccountLibraryStore store;

    @Autowired
    UserControllerTest(MockMvc mvc, AccountLibraryStore store) {
        this.mvc = mvc;
        this.store = store;
    }

    @Test
    void 비회원은_공개_이야기를_읽고_계정_저장에_접근할_수_없다() throws Exception {
        mvc.perform(get("/v1/stories/story-published-1")).andExpect(status().isOk());
        mvc.perform(get("/v1/courses")).andExpect(status().isOk());
        mvc.perform(get(LIBRARY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(put(STORY)).andExpect(status().isUnauthorized());
        mvc.perform(put(COURSE)).andExpect(status().isUnauthorized());
        mvc.perform(put(HISTORY + "story-published-1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"playedAt\":\"2026-01-01T00:00:00Z\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 저장_재전송과_삭제는_멱등이고_계정마다_분리된다() throws Exception {
        var owner = user("save-owner");
        var other = user("save-other");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(put(STORY).with(owner)).andExpect(status().isNoContent());
            mvc.perform(put(COURSE).with(owner)).andExpect(status().isNoContent());
        }

        mvc.perform(get(LIBRARY).with(other).header("X-Account-Id", "save-owner"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value("save-other"))
            .andExpect(jsonPath("$.savedStories.length()").value(0))
            .andExpect(jsonPath("$.savedCourses.length()").value(0))
            .andExpect(jsonPath("$.history.length()").value(0));
        mvc.perform(delete(STORY).with(other)).andExpect(status().isNoContent());
        mvc.perform(delete(COURSE).with(other)).andExpect(status().isNoContent());
        mvc.perform(get(LIBRARY).with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value("save-owner"))
            .andExpect(jsonPath("$.savedStories.length()").value(1))
            .andExpect(jsonPath("$.savedStories[0].id").value("story-published-1"))
            .andExpect(jsonPath("$.savedStories[0].category").value("INDUSTRY"))
            .andExpect(jsonPath("$.savedCourses.length()").value(1))
            .andExpect(jsonPath("$.savedCourses[0].id").value("course-seongsu"));

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete(STORY).with(owner)).andExpect(status().isNoContent());
            mvc.perform(delete(COURSE).with(owner)).andExpect(status().isNoContent());
        }
        mvc.perform(get(LIBRARY).with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savedStories.length()").value(0))
            .andExpect(jsonPath("$.savedCourses.length()").value(0));
    }

    @Test
    void 청취_기록은_최신_시각을_유지하고_시간순으로_표시한다() throws Exception {
        var owner = user("listening-owner");
        var other = user("listening-other");
        putHistory("story-published-1", "2026-01-01T00:00:03Z", owner);
        putHistory("story-published-1", "2026-01-01T00:00:03Z", owner);
        putHistory("story-published-2", "2026-01-01T00:00:02Z", owner);
        putHistory("story-published-1", "2026-01-01T00:00:01Z", owner);
        putHistory("story-published-1", "2026-01-01T00:00:01Z", other);

        mvc.perform(get(LIBRARY).with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.history.length()").value(2))
            .andExpect(jsonPath("$.history[0].id").value("story-published-1"))
            .andExpect(jsonPath("$.history[0].playedAt").value("2026-01-01T00:00:03Z"))
            .andExpect(jsonPath("$.history[1].id").value("story-published-2"));
        mvc.perform(get(LIBRARY).with(other))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.history.length()").value(1))
            .andExpect(jsonPath("$.history[0].playedAt").value("2026-01-01T00:00:01Z"));
    }

    @Test
    void 비공개_이야기와_알_수_없는_코스는_저장이나_조회에서_제외한다() throws Exception {
        var owner = user("published-only");
        mvc.perform(put(ROOT + "/saved-stories/story-draft-1").with(owner))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));
        mvc.perform(put(ROOT + "/saved-courses/unknown").with(owner))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        mvc.perform(put(HISTORY + "story-draft-1").with(owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"playedAt\":\"2026-01-01T00:00:00Z\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));

        store.save("published-only", AccountLibraryStore.Kind.STORY, "story-draft-1");
        store.save("published-only", AccountLibraryStore.Kind.COURSE, "retired-course");
        store.recordListening("published-only", "story-draft-1", Instant.parse("2026-01-01T00:00:00Z"));
        mvc.perform(get(LIBRARY).with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savedStories.length()").value(0))
            .andExpect(jsonPath("$.savedCourses.length()").value(0))
            .andExpect(jsonPath("$.history.length()").value(0));
    }

    @Test
    void 청취_시각과_설정의_잘못된_요청은_안정적인_오류를_반환한다() throws Exception {
        var owner = user("invalid-payload");
        for (String body : new String[]{"{}", "{\"playedAt\":1710000000}",
                "{\"playedAt\":\"1900-01-01T00:00:00Z\"}", "{\"playedAt\":\"9999-01-01T00:00:00Z\"}"}) {
            mvc.perform(put(HISTORY + "story-published-1").with(owner).contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(put(PREFERENCES).with(owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"categories\":{\"HISTORY\":\"INVALID\"},\"autoplay\":false}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(put(PREFERENCES).with(owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"categories\":{}}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 청취_설정은_다른_계정에_영향을_주지_않는다() throws Exception {
        var owner = user("preference-owner");
        mvc.perform(put(PREFERENCES).with(owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"categories\":{\"HISTORY\":\"FREQUENT\"},\"autoplay\":false,\"familyMode\":true}"))
            .andExpect(status().isOk());
        mvc.perform(get(PREFERENCES).with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories.HISTORY").value("FREQUENT"))
            .andExpect(jsonPath("$.autoplay").value(false))
            .andExpect(jsonPath("$.familyMode").value(true));
        mvc.perform(get(PREFERENCES).with(user("preference-other")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories").isEmpty())
            .andExpect(jsonPath("$.autoplay").value(true))
            .andExpect(jsonPath("$.familyMode").value(false));
    }

    @Test
    void 청취_이력은_공개_이야기_필터를_먼저_적용한_뒤_최신_100건을_유지한다() {
        var published = new HashSet<String>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int index = 0; index < 105; index++) {
            String storyId = "history-limit-story-" + index;
            published.add(storyId);
            store.recordListening("history-limit-owner", storyId, start.plusSeconds(index));
        }
        store.recordListening("history-limit-owner", "history-limit-unpublished", start.plusSeconds(106));
        var history = store.library("history-limit-owner", published);
        assertEquals(100, history.size());
        assertEquals("history-limit-story-104", history.get(0).id());
        assertEquals("history-limit-story-5", history.get(99).id());
    }

    private void putHistory(String storyId, String time,
                            org.springframework.test.web.servlet.request.RequestPostProcessor owner) throws Exception {
        mvc.perform(put(HISTORY + storyId).with(owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"playedAt\":\"" + time + "\"}"))
            .andExpect(status().isNoContent());
    }
}
