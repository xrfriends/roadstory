package com.roadstory.story.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.roadstory.common.error.ApiException;
import com.roadstory.common.geo.GeoPoint;
import com.roadstory.story.application.StoryService;
import com.roadstory.story.domain.SourceReference;
import com.roadstory.story.domain.Story;
import com.roadstory.story.infrastructure.StoryAudioFiles;
import com.roadstory.story.infrastructure.StoryAudioFiles.AudioDescriptor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/stories")
public class StoryController {
    private final StoryService storyService;
    private final StoryAudioFiles audioFiles;

    public StoryController(StoryService storyService, StoryAudioFiles audioFiles) {
        this.storyService = storyService;
        this.audioFiles = audioFiles;
    }

    @GetMapping("/nearby")
    public NearbyStoriesResponse nearby(
        @RequestParam double latitude,
        @RequestParam double longitude,
        @RequestParam(required = false) Double heading,
        @RequestParam(required = false) Double speedMps,
        @RequestParam(defaultValue = "1500") @Min(100) @Max(5000) int radiusMeters
    ) {
        if (!GeoPoint.isValid(latitude, longitude)
            || (heading != null && (!Double.isFinite(heading) || heading < 0 || heading > 360))
            || (speedMps != null && (!Double.isFinite(speedMps) || speedMps < 0))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다.");
        }
        var location = new GeoPoint(latitude, longitude);
        return new NearbyStoriesResponse(storyService.nearby(location, heading, speedMps, radiusMeters).stream()
            .map(candidate -> StorySummaryResponse.from(candidate.story(), candidate.distanceMeters()))
            .toList());
    }

    @GetMapping("/{storyId}")
    public StoryDetailResponse detail(@PathVariable String storyId) {
        Story story = storyService.detail(storyId);
        AudioDescriptor audio = audioFiles.find(story).map(StoryAudioFiles.AudioFile::descriptor).orElse(null);
        return new StoryDetailResponse(story.id(), story.title(), story.placeName(), story.category(),
            story.familyFriendly(), 0, story.durationSeconds(), story.hook(), false, "PUBLISHED",
            "2026-08-04T00:00:00Z", story.sources(), story.narrationText(), audio);
    }

    @GetMapping("/{storyId}/audio")
    public ResponseEntity<InputStreamResource> audio(@PathVariable String storyId) {
        var file = audioFiles.find(storyService.detail(storyId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORY_AUDIO_NOT_FOUND", "Story 음원을 찾을 수 없습니다."));
        var descriptor = file.descriptor();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(descriptor.contentType()))
            .contentLength(descriptor.byteLength())
            .eTag(descriptor.sha256())
            .cacheControl(CacheControl.noCache())
            .body(new InputStreamResource(file::open));
    }

    public record StoryDetailResponse(String id, String title, String placeName, String category,
                                      boolean familyFriendly, int distanceMeters, int durationSeconds, String hook,
                                      boolean saved, String verificationStatus, String reviewedAt,
                                      List<SourceReference> sources, String fullText,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) AudioDescriptor audio) {}
    public record NearbyStoriesResponse(List<StorySummaryResponse> items) {}
}
