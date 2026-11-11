package com.roadstory.story.application;

import com.roadstory.story.domain.Story;
import java.util.List;
import java.util.Optional;

public interface StoryRepository {
    List<Story> findAll();
    Optional<Story> findById(String id);
}
