package com.roadstory.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
class InMemoryAccountLibraryStore implements AccountLibraryStore {
    private static final Comparator<Entry> RECENT_FIRST =
        Comparator.comparing(Entry::recordedAt).reversed().thenComparing(Entry::id);

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public List<Entry> library(String accountId, Set<String> publishedStoryIds) {
        Account account = accounts.get(accountId);
        if (account == null) {
            return List.of();
        }
        synchronized (account) {
            List<Entry> entries = new ArrayList<>(account.saved.values());
            account.history.entrySet().stream()
                .filter(entry -> publishedStoryIds.contains(entry.getKey()))
                .map(entry -> new Entry(Kind.HISTORY, entry.getKey(), entry.getValue()))
                .sorted(RECENT_FIRST)
                .limit(HISTORY_LIMIT)
                .forEach(entries::add);
            entries.sort(RECENT_FIRST);
            return entries;
        }
    }

    @Override
    public void save(String accountId, Kind kind, String id) {
        Account account = account(accountId);
        synchronized (account) {
            account.saved.putIfAbsent(new SavedKey(kind, id), new Entry(kind, id, Instant.now()));
        }
    }

    @Override
    public void remove(String accountId, Kind kind, String id) {
        Account account = accounts.get(accountId);
        if (account != null) {
            synchronized (account) {
                account.saved.remove(new SavedKey(kind, id));
            }
        }
    }

    @Override
    public void recordListening(String accountId, String storyId, Instant playedAt) {
        Account account = account(accountId);
        synchronized (account) {
            account.history.merge(storyId, playedAt, (previous, incoming) ->
                incoming.isAfter(previous) ? incoming : previous);
        }
    }

    @Override
    public Optional<UserPreferences> preferences(String accountId) {
        Account account = accounts.get(accountId);
        return account == null ? Optional.empty() : Optional.ofNullable(account.preferences);
    }

    @Override
    public void setPreferences(String accountId, UserPreferences preferences) {
        account(accountId).preferences = preferences;
    }

    private Account account(String accountId) {
        return accounts.computeIfAbsent(accountId, ignored -> new Account());
    }

    private record SavedKey(Kind kind, String id) {}

    private static class Account {
        private final Map<SavedKey, Entry> saved = new HashMap<>();
        private final Map<String, Instant> history = new HashMap<>();
        private volatile UserPreferences preferences;
    }
}
