package com.roadstory.journey;

import com.roadstory.RoadStoryApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RoadStoryApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DestinationPlaylistControllerTest {
    private final MockMvc mvc;

    @Autowired
    DestinationPlaylistControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void 경로가_없으면_목적지_주변만_편성한다() throws Exception {
        compose("""
            {"destination":{"latitude":37.544628,"longitude":127.055983},
             "remainingDurationSeconds":120}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.basis").value("DESTINATION_NEARBY"))
            .andExpect(jsonPath("$.totalAudioDurationSeconds").value(72))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].id").value("story-published-1"))
            .andExpect(jsonPath("$.items[1].id").value("story-published-2"))
            .andExpect(jsonPath("$.destination").doesNotExist());

        compose("""
            {"destination":{"latitude":37.5,"longitude":127.0},"remainingDurationSeconds":120}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 목적지_도착_여유와_듣기_모드를_적용한다() throws Exception {
        compose("""
            {"destination":{"latitude":37.544628,"longitude":127.055983},
             "remainingDurationSeconds":89,"mode":"STANDARD"}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAudioDurationSeconds").value(12));

        for (String mode : new String[]{"SHORT", "FAMILY"}) {
            compose("""
                {"destination":{"latitude":37.544628,"longitude":127.055983},
                 "remainingDurationSeconds":120,"mode":"%s"}
                """.formatted(mode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("story-published-2"));
        }
    }

    @Test
    void 제공된_경로의_진행_순서와_거리로_편성한다() throws Exception {
        compose("""
            {"origin":{"latitude":37.547,"longitude":127.056},
             "destination":{"latitude":37.544,"longitude":127.056},
             "remainingDurationSeconds":120,"routePolyline":"wkddF_snfWvQ?"}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.basis").value("ROUTE"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].id").value("story-published-2"))
            .andExpect(jsonPath("$.items[1].id").value("story-published-1"));
        compose("""
            {"origin":{"latitude":37.547,"longitude":127.062},
             "destination":{"latitude":37.544,"longitude":127.062},
             "remainingDurationSeconds":120,"routePolyline":"wkddFoxofWvQ?"}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.basis").value("ROUTE"))
            .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 유효하지_않은_경로를_목적지_주변으로_대체하지_않는다() throws Exception {
        compose("""
            {"destination":{"latitude":37.544,"longitude":127.056},
             "remainingDurationSeconds":120,"routePolyline":"wkddF_snfWvQ?"}
            """)
            .andExpect(status().isUnprocessableContent())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("INVALID_ROUTE_GEOMETRY"));

        for (String polyline : new String[]{"broken!", "wkddFoxofWvQ?"}) {
            compose("""
                {"origin":{"latitude":37.547,"longitude":127.056},
                 "destination":{"latitude":37.544,"longitude":127.056},
                 "remainingDurationSeconds":120,"routePolyline":"%s"}
                """.formatted(polyline))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTE_GEOMETRY"));
        }
    }

    @Test
    void 요청_오류는_원본_입력_없이_문제_코드를_반환한다() throws Exception {
        compose("""
            {"destination":{"latitude":999,"longitude":127.056},"remainingDurationSeconds":120}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.detail").value("요청 값이 올바르지 않습니다."));
        compose("""
            {"destination":{"latitude":37.54,"longitude":127.056},"remainingDurationSeconds":59}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private ResultActions compose(String body) throws Exception {
        return mvc.perform(post("/v1/journeys/destination-playlist")
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
