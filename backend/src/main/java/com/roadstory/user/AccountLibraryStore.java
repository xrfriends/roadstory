package com.roadstory.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

interface AccountLibraryStore {
    int HISTORY_LIMIT = 100;

    enum Kind { STORY, COURSE, HISTORY }

    record Entry(Kind kind, String id, Instant recordedAt) {}

    List<Entry> library(String accountId, Set<String> publishedStoryIds);

    void save(String accountId, Kind kind, String id);

    void remove(String accountId, Kind kind, String id);

    void recordListening(String accountId, String storyId, Instant playedAt);

    Optional<UserPreferences> preferences(String accountId);

    void setPreferences(String accountId, UserPreferences preferences);
}
