package com.roadstory.story.domain;

import com.roadstory.common.geo.GeoPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryTest {
    @Test
    void source_없는_Story는_발행할_수_없다() {
        Story story = new Story("draft", "제목", "장소", "HISTORY", false, null, 30, "hook", "낭독문",
            VerificationStatus.DRAFT, List.of());
        assertThrows(IllegalArgumentException.class, story::publish);
    }

    @Test
    void Story를_발행해도_가족_적합_표시가_유지된다() {
        Story story = new Story("verified", "제목", "장소", "CHILDREN", true, new GeoPoint(30, 120), 30, "hook", "낭독문",
            VerificationStatus.VERIFIED, List.of(new SourceReference("기관", "출처", "B")));

        Story published = story.publish();

        assertEquals(VerificationStatus.PUBLISHED, published.verificationStatus());
        assertTrue(published.familyFriendly());
        assertEquals(story.anchor(), published.anchor());
    }
}
