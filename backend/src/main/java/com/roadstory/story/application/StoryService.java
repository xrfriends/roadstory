package com.roadstory.story.application;

import com.roadstory.common.error.ApiException;
import com.roadstory.common.geo.GeoPoint;
import com.roadstory.story.domain.Story;
import com.roadstory.story.domain.VerificationStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StoryService {
    private final StoryRepository repository;

    public StoryService(StoryRepository repository) {
        this.repository = repository;
    }

    public List<Story> published() {
        return repository.findAll().stream()
            .filter(story -> story.verificationStatus() == VerificationStatus.PUBLISHED)
            .toList();
    }

    public List<NearbyStory> nearby(GeoPoint location, Double heading, Double speedMps, int radiusMeters) {
        return published().stream()
            .filter(story -> story.anchor() != null)
            .map(story -> new NearbyStory(story, location.distanceTo(story.anchor())))
            .filter(candidate -> candidate.distanceMeters() <= radiusMeters)
            .sorted(Comparator.comparing((NearbyStory candidate) -> behind(location, heading, speedMps, candidate))
                .thenComparingDouble(NearbyStory::distanceMeters))
            .toList();
    }

    private boolean behind(GeoPoint location, Double heading, Double speedMps, NearbyStory candidate) {
        if (heading == null || speedMps == null || speedMps < 3 || candidate.distanceMeters() <= 50) {
            return false;
        }
        double bearing = location.bearingTo(candidate.story().anchor());
        return Math.abs(Math.IEEEremainder(bearing - heading, 360)) > 90;
    }

    public Story detail(String id) {
        return findPublished(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORY_NOT_FOUND", "Story를 찾을 수 없습니다."));
    }

    public Optional<Story> findPublished(String id) {
        return repository.findById(id).filter(story -> story.verificationStatus() == VerificationStatus.PUBLISHED);
    }

    public record NearbyStory(Story story, double distanceMeters) {}
}
