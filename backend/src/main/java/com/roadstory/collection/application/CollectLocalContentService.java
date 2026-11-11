package com.roadstory.collection.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstory.collection.application.CollectionPorts.CollectionJobRepository;
import com.roadstory.collection.application.CollectionPorts.DataSourceRepository;
import com.roadstory.collection.application.CollectionPorts.RawRecordRepository;
import com.roadstory.collection.application.CollectionPorts.RawRecordSaveResult;
import com.roadstory.collection.domain.CollectionJob;
import com.roadstory.collection.domain.CollectionJobStatus;
import com.roadstory.collection.domain.RawRecord;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local")
public class CollectLocalContentService {
    private final Map<String, LocalContentSource> sources;
    private final DataSourceRepository dataSources;
    private final CollectionJobRepository jobs;
    private final RawRecordRepository rawRecords;
    private final ContentHasher hasher;
    private final Clock clock;
    private final int maxNetworkAttempts;
    private final long retryDelayMillis;

    @Autowired
    public CollectLocalContentService(List<LocalContentSource> sources, DataSourceRepository dataSources,
                                      CollectionJobRepository jobs, RawRecordRepository rawRecords,
                                      ObjectMapper objectMapper) {
        this(sources, dataSources, jobs, rawRecords, objectMapper, Clock.systemUTC(), 3, 100);
    }

    public CollectLocalContentService(List<LocalContentSource> sources, DataSourceRepository dataSources,
                                      CollectionJobRepository jobs, RawRecordRepository rawRecords,
                                      ObjectMapper objectMapper, Clock clock, int maxNetworkAttempts,
                                      long retryDelayMillis) {
        this.sources = new HashMap<>();
        sources.forEach(source -> this.sources.put(source.sourceCode(), source));
        this.dataSources = dataSources;
        this.jobs = jobs;
        this.rawRecords = rawRecords;
        this.hasher = new ContentHasher(objectMapper);
        this.clock = clock;
        this.maxNetworkAttempts = maxNetworkAttempts;
        this.retryDelayMillis = retryDelayMillis;
    }

    public CollectionJob run(String sourceCode) {
        var definition = dataSources.findBySourceCode(sourceCode)
            .orElseThrow(() -> new UnknownSourceException(sourceCode));
        if (!definition.enabled()) {
            throw new SourceDisabledException(sourceCode);
        }
        LocalContentSource source = sources.get(sourceCode);
        if (source == null) {
            throw new UnknownSourceException(sourceCode);
        }

        CollectionJob job = jobs.save(CollectionJob.running(sourceCode, clock.instant()));
        CollectionCursor cursor = null;
        int requested = 0;
        int received = 0;
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        Set<String> seen = new HashSet<>();

        try {
            do {
                CollectionPage page = collectWithRetry(source, cursor);
                requested += page.requestedCount();
                received += page.records().size();
                for (CollectedRecord item : page.records()) {
                    if (item instanceof CollectedRecord.Invalid) {
                        failed++;
                        continue;
                    }
                    try {
                        CollectedRecord.Valid valid = (CollectedRecord.Valid) item;
                        if (valid.externalId() == null || valid.externalId().isBlank()) {
                            throw new IllegalArgumentException("externalId is required");
                        }
                        if (!seen.add(valid.externalId())) {
                            failed++;
                            continue;
                        }
                        RawRecordSaveResult result = rawRecords.saveIfChanged(new RawRecord(null,
                            sourceCode, valid.externalId(), clock.instant(), valid.sourceUpdatedAt(),
                            hasher.hash(valid.rawPayload()), valid.sourceUrl(), valid.rawPayload().deepCopy()));
                        switch (result) {
                            case CREATED -> created++;
                            case UPDATED -> updated++;
                            case UNCHANGED -> unchanged++;
                        }
                    } catch (RuntimeException exception) {
                        failed++;
                    }
                }
                cursor = page.nextCursor();
            } while (cursor != null);
        } catch (RuntimeException exception) {
            return jobs.save(job.complete(clock.instant(), CollectionJobStatus.FAILED, requested,
                received, created, updated, unchanged, failed));
        }

        CollectionJobStatus status = failed == 0
            ? CollectionJobStatus.SUCCESS : CollectionJobStatus.PARTIAL_SUCCESS;
        return jobs.save(job.complete(clock.instant(), status, requested, received, created,
            updated, unchanged, failed));
    }

    private CollectionPage collectWithRetry(LocalContentSource source, CollectionCursor cursor) {
        int attempt = 1;
        while (true) {
            try {
                return source.collect(cursor);
            } catch (SourceCollectionException exception) {
                boolean retryable = exception instanceof SourceCollectionException.Network
                    || exception instanceof SourceCollectionException.HttpServer server && server.status() >= 500;
                if (!retryable || attempt >= maxNetworkAttempts) {
                    throw exception;
                }
                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new SourceCollectionException.NonRetryable("Collection retry interrupted");
                }
                attempt++;
            }
        }
    }

    public static final class SourceDisabledException extends IllegalStateException {
        public SourceDisabledException(String sourceCode) {
            super("Disabled source cannot run: " + sourceCode);
        }
    }

    public static final class UnknownSourceException extends IllegalArgumentException {
        public UnknownSourceException(String sourceCode) {
            super("Unknown source: " + sourceCode);
        }
    }
}
