package com.roadstory.story.api;

import com.roadstory.RoadStoryApplication;
import com.roadstory.story.application.StoryService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RoadStoryApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StoryAudioControllerTest {
    @TempDir
    static Path audioDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StoryService stories;

    @DynamicPropertySource
    static void configureAudio(DynamicPropertyRegistry properties) {
        properties.add("roadstory.audio.directory", () -> audioDirectory.toString());
    }

    @Test
    void publishedNarrationIncludesTheVersionAndDownloadsTheMatchingBytes() throws Exception {
        String storyId = "story-published-1";
        String path = "/v1/stories/" + storyId;
        byte[] audio = {1, 2, 3, 4};
        putAudio(storyId, audio, stories.detail(storyId).narrationText());
        String sha256 = digest(audio);

        mockMvc.perform(get(path))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.audio.url").value(path + "/audio"))
            .andExpect(jsonPath("$.audio.contentType").value("audio/mp4"))
            .andExpect(jsonPath("$.audio.byteLength").value(audio.length))
            .andExpect(jsonPath("$.audio.sha256").value(sha256))
            .andExpect(jsonPath("$.audio.durationMs").value(12_000));

        String etag = '"' + sha256 + '"';
        mockMvc.perform(get(path + "/audio"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mp4"))
            .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.length)))
            .andExpect(header().string(HttpHeaders.ETAG, etag))
            .andExpect(content().bytes(audio));

        mockMvc.perform(get(path + "/audio").header(HttpHeaders.IF_NONE_MATCH, etag))
            .andExpect(status().isNotModified());

        byte[] updated = {4, 3, 2, 1};
        Path replacement = Files.write(audioDirectory.resolve("replacement.m4a"), updated);
        Files.move(replacement, audioDirectory.resolve(storyId + ".m4a"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        mockMvc.perform(get(path + "/audio").header(HttpHeaders.IF_NONE_MATCH, etag))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ETAG, '"' + digest(updated) + '"'))
            .andExpect(content().bytes(updated));
    }

    @Test
    void aRecordingForOldNarrationIsUnavailable() throws Exception {
        String storyId = "story-published-2";
        putAudio(storyId, new byte[]{1}, "이전 낭독문");
        mockMvc.perform(get("/v1/stories/" + storyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.audio").doesNotHaveJsonPath());
        mockMvc.perform(get("/v1/stories/" + storyId + "/audio"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.code").value("STORY_AUDIO_NOT_FOUND"));
    }

    @Test
    void supplyingAnAudioFileCannotPublishADraftStory() throws Exception {
        putAudio("story-draft-1", new byte[]{1}, "검토 중인 낭독문");
        mockMvc.perform(get("/v1/stories/story-draft-1/audio"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));
    }

    private static void putAudio(String storyId, byte[] audio, String narration) throws Exception {
        Files.write(audioDirectory.resolve(storyId + ".m4a"), audio);
        Files.writeString(audioDirectory.resolve(storyId + ".json"),
            "{\"durationMs\":12000,\"narrationSha256\":\"" + digest(narration.getBytes(StandardCharsets.UTF_8)) + "\"}");
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
