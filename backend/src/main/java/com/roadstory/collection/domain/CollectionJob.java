package com.roadstory.collection.domain;

import java.time.Instant;

public record CollectionJob(Long id, String sourceCode, Instant startedAt, Instant completedAt,
                            CollectionJobStatus status, int requestedCount, int receivedCount,
                            int createdCount, int updatedCount, int unchangedCount, int failedCount) {
    public static CollectionJob running(String sourceCode, Instant startedAt) {
        return new CollectionJob(null, sourceCode, startedAt, null, CollectionJobStatus.RUNNING,
            0, 0, 0, 0, 0, 0);
    }

    public CollectionJob complete(Instant at, CollectionJobStatus completedStatus, int requested,
                                  int received, int created, int updated, int unchanged, int failed) {
        return new CollectionJob(id, sourceCode, startedAt, at, completedStatus, requested, received,
            created, updated, unchanged, failed);
    }

    public CollectionJob withId(Long savedId) {
        return new CollectionJob(savedId, sourceCode, startedAt, completedAt, status, requestedCount,
            receivedCount, createdCount, updatedCount, unchangedCount, failedCount);
    }
}
