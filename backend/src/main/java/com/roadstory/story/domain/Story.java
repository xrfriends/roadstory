package com.roadstory.story.domain;

import com.roadstory.common.geo.GeoPoint;
import java.util.List;

public record Story(String id, String title, String placeName, String category, boolean familyFriendly, GeoPoint anchor,
                    int durationSeconds, String hook, String narrationText, VerificationStatus verificationStatus,
                    List<SourceReference> sources) {
    public Story {
        sources = List.copyOf(sources);
    }

    public Story publish() {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Source가 없는 Story는 PUBLISHED 상태가 될 수 없습니다.");
        }
        return new Story(id, title, placeName, category, familyFriendly, anchor, durationSeconds, hook,
            narrationText, VerificationStatus.PUBLISHED, sources);
    }
}
