package com.roadstory.journey;

import com.roadstory.common.error.ApiException;
import com.roadstory.common.geo.GeoPoint;
import com.roadstory.story.api.StorySummaryResponse;
import com.roadstory.story.application.StoryService;
import com.roadstory.story.domain.Story;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/journeys")
public class DestinationPlaylistController {
    private static final int DESTINATION_RADIUS_METERS = 300;
    private static final int ROUTE_CORRIDOR_METERS = 100;
    private static final int ARRIVAL_BUFFER_SECONDS = 30;
    private static final int SHORT_AUDIO_CAP_SECONDS = 180;
    private final StoryService stories;

    public DestinationPlaylistController(StoryService stories) {
        this.stories = stories;
    }

    @PostMapping("/destination-playlist")
    public PlaylistResponse compose(@Valid @RequestBody PlaylistRequest request) {
        var mode = parseMode(request.mode());
        var destination = request.destination().point();
        var origin = request.origin() == null ? null : request.origin().point();
        var route = request.routePolyline() == null
            ? null : RouteGeometry.parse(request.routePolyline(), origin, destination);
        var candidates = stories.published().stream()
            .filter(story -> story.anchor() != null && story.durationSeconds() > 0)
            .filter(story -> mode != Mode.FAMILY || story.familyFriendly())
            .map(story -> locate(story, route, destination))
            .filter(candidate -> candidate.distanceMeters() <=
                (route == null ? DESTINATION_RADIUS_METERS : ROUTE_CORRIDOR_METERS))
            .sorted(Comparator.comparingDouble(Candidate::order).thenComparingDouble(Candidate::distanceMeters))
            .toList();

        int budget = request.remainingDurationSeconds() - ARRIVAL_BUFFER_SECONDS;
        if (mode == Mode.SHORT) {
            budget = Math.min(budget / 2, SHORT_AUDIO_CAP_SECONDS);
        }
        int totalAudioSeconds = 0;
        var items = new ArrayList<StorySummaryResponse>();
        for (Candidate candidate : candidates) {
            int duration = candidate.story().durationSeconds();
            if (duration <= budget - totalAudioSeconds) {
                items.add(StorySummaryResponse.from(candidate.story(), candidate.distanceMeters()));
                totalAudioSeconds += duration;
            }
        }
        return new PlaylistResponse(UUID.randomUUID().toString(),
            route == null ? Basis.DESTINATION_NEARBY : Basis.ROUTE, totalAudioSeconds, List.copyOf(items));
    }

    private static Candidate locate(Story story, RouteGeometry route, GeoPoint destination) {
        if (route == null) {
            double distance = destination.distanceTo(story.anchor());
            return new Candidate(story, distance, distance);
        }
        var projection = route.closestTo(story.anchor());
        return new Candidate(story, projection.distanceMeters(), projection.progressMeters());
    }

    private static Mode parseMode(String mode) {
        if (mode == null) {
            return Mode.STANDARD;
        }
        try {
            return Mode.valueOf(mode);
        } catch (IllegalArgumentException ignored) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다.");
        }
    }

    private record Candidate(Story story, double distanceMeters, double order) {}
    private enum Mode { SHORT, STANDARD, FAMILY }
    public enum Basis { ROUTE, DESTINATION_NEARBY }
    public record PlaylistResponse(String id, Basis basis, int totalAudioDurationSeconds,
                                   List<StorySummaryResponse> items) {}

    public record PlaylistRequest(
        @Valid Coordinates origin,
        @NotNull @Valid Coordinates destination,
        @NotNull @Min(60) Integer remainingDurationSeconds,
        String mode,
        String routePolyline
    ) {}

    public record Coordinates(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude
    ) {
        GeoPoint point() {
            if (!GeoPoint.isValid(latitude, longitude)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다.");
            }
            return new GeoPoint(latitude, longitude);
        }
    }
}
