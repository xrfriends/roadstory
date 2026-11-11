package com.roadstory.story.infrastructure;

import com.roadstory.common.geo.GeoPoint;
import com.roadstory.story.application.StoryRepository;
import com.roadstory.story.domain.SourceReference;
import com.roadstory.story.domain.Story;
import com.roadstory.story.domain.VerificationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryStoryRepository implements StoryRepository {
    private static final List<Story> BASE_STORIES = List.of(
        new Story("story-published-1", "성수동 수제화 거리", "성수동", "INDUSTRY", false, PublicStoryLocations.SEONGSU_STATION, 60,
            "공장 골목은 어떻게 창작 거리가 되었을까요?",
            "성수동 수제화 거리는 1970년대부터 신발 공장과 장인들의 작업장이 모이며 형성되었습니다. "
                + "오늘날에도 골목 곳곳에서 수제화 제작 기술과 산업의 흔적을 만날 수 있습니다.",
            VerificationStatus.PUBLISHED,
            List.of(new SourceReference("성동구청", "성수동 산업사", "B"))),
        new Story("story-draft-1", "검수 중인 이야기", "성수동", "HISTORY", false, PublicStoryLocations.SEONGSU_STATION, 30,
            "아직 공개되지 않은 이야기", "검수 전 낭독문", VerificationStatus.DRAFT, List.of())
    );

    private static final Story LOCAL_QUEUE_DEMO = new Story(
        "story-published-2", "두 번째 데모 이야기", "성수동", "DAILY_LIFE", true, new GeoPoint(37.5461, 127.056), 12,
        "다음 이야기가 이어지는지 확인하는 재생 샘플입니다.",
        "두 번째 데모 이야기입니다. 이 낭독은 로드스토리의 다음 이야기와 자동 재생을 확인하는 샘플입니다.",
        VerificationStatus.PUBLISHED,
        List.of(new SourceReference("Road Story", "로컬 재생 큐 검증용 샘플", "E"))
    );

    private final List<Story> stories;

    public InMemoryStoryRepository(Environment environment) {
        List<String> profiles = List.of(environment.getActiveProfiles());
        if (profiles.isEmpty()) {
            profiles = List.of(environment.getDefaultProfiles());
        }
        List<Story> entries = new ArrayList<>(BASE_STORIES);
        // Synthetic narration is available only in the standalone local demo profile.
        if (profiles.equals(List.of("local"))) {
            entries.add(LOCAL_QUEUE_DEMO);
        }
        stories = List.copyOf(entries);
    }

    @Override
    public List<Story> findAll() {
        return stories;
    }

    @Override
    public Optional<Story> findById(String id) {
        return stories.stream().filter(story -> story.id().equals(id)).findFirst();
    }
}
