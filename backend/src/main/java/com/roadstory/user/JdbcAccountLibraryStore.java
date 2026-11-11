package com.roadstory.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local")
class JdbcAccountLibraryStore implements AccountLibraryStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, UserPreferences.Frequency>> FREQUENCIES = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAccountLibraryStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Entry> library(String accountId, Set<String> publishedStoryIds) {
        return jdbc.query("""
            SELECT kind, item_id, saved_at AS recorded_at
            FROM account_saved_item WHERE account_id = :accountId
            UNION ALL
            SELECT 'HISTORY', story_id, played_at FROM (
                SELECT story_id, played_at FROM account_listening_history
                WHERE account_id = :accountId AND story_id IN (:storyIds)
                ORDER BY played_at DESC, story_id LIMIT :historyLimit
            ) AS recent
            ORDER BY recorded_at DESC, item_id
            """, Map.of("accountId", accountId, "historyLimit", HISTORY_LIMIT,
                         "storyIds", publishedStoryIds.isEmpty() ? Set.of("") : publishedStoryIds),
            (row, index) -> new Entry(Kind.valueOf(row.getString("kind")), row.getString("item_id"),
                row.getObject("recorded_at", OffsetDateTime.class).toInstant()));
    }

    @Override
    public void save(String accountId, Kind kind, String id) {
        jdbc.update("""
            INSERT INTO account_saved_item (account_id, kind, item_id) VALUES (:accountId, :kind, :id)
            ON CONFLICT (account_id, kind, item_id) DO NOTHING
            """, itemKey(accountId, kind, id));
    }

    @Override
    public void remove(String accountId, Kind kind, String id) {
        jdbc.update("""
            DELETE FROM account_saved_item WHERE account_id = :accountId AND kind = :kind AND item_id = :id
            """, itemKey(accountId, kind, id));
    }

    @Override
    public void recordListening(String accountId, String storyId, Instant playedAt) {
        jdbc.update("""
            INSERT INTO account_listening_history (account_id, story_id, played_at)
            VALUES (:accountId, :storyId, :playedAt)
            ON CONFLICT (account_id, story_id) DO UPDATE SET played_at = EXCLUDED.played_at
            WHERE account_listening_history.played_at < EXCLUDED.played_at
            """, Map.of("accountId", accountId, "storyId", storyId,
                "playedAt", playedAt.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public Optional<UserPreferences> preferences(String accountId) {
        return jdbc.query("""
            SELECT categories, autoplay, family_mode FROM account_preferences WHERE account_id = :accountId
            """, Map.of("accountId", accountId), (row, index) -> new UserPreferences(
            readFrequencies(row.getString("categories")), row.getBoolean("autoplay"), row.getBoolean("family_mode")))
            .stream().findFirst();
    }

    @Override
    public void setPreferences(String accountId, UserPreferences preferences) {
        jdbc.update("""
            INSERT INTO account_preferences (account_id, categories, autoplay, family_mode)
            VALUES (:accountId, CAST(:categories AS jsonb), :autoplay, :familyMode)
            ON CONFLICT (account_id) DO UPDATE SET categories = EXCLUDED.categories,
                autoplay = EXCLUDED.autoplay, family_mode = EXCLUDED.family_mode
            """, Map.of("accountId", accountId, "categories", writeFrequencies(preferences.categories()),
                "autoplay", preferences.autoplay(), "familyMode", preferences.familyMode()));
    }

    private static Map<String, String> itemKey(String accountId, Kind kind, String id) {
        return Map.of("accountId", accountId, "kind", kind.name(), "id", id);
    }

    private static Map<String, UserPreferences.Frequency> readFrequencies(String json) {
        try {
            return JSON.readValue(json, FREQUENCIES);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 청취 설정을 읽지 못했습니다.");
        }
    }

    private static String writeFrequencies(Map<String, UserPreferences.Frequency> categories) {
        try {
            return JSON.writeValueAsString(categories);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("청취 설정을 저장하지 못했습니다.");
        }
    }
}
