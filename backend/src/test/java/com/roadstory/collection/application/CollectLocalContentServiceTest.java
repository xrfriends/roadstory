package com.roadstory.collection.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstory.collection.application.CollectLocalContentService.SourceDisabledException;
import com.roadstory.collection.application.CollectionPorts.CollectionJobRepository;
import com.roadstory.collection.application.CollectionPorts.DataSourceRepository;
import com.roadstory.collection.application.CollectionPorts.RawRecordRepository;
import com.roadstory.collection.application.CollectionPorts.RawRecordSaveResult;
import com.roadstory.collection.domain.CollectionJob;
import com.roadstory.collection.domain.CollectionJobStatus;
import com.roadstory.collection.domain.DataSource;
import com.roadstory.collection.domain.RawRecord;
import com.roadstory.collection.infrastructure.source.FakeLocalContentSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollectLocalContentServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);

    @Test // TS-COLLECT-001
    void new_raw_record_is_stored() {
        Fixture fixture = fixture(pages(record("1", "old")), true, null);
        CollectionJob job = fixture.service.run("TEST");
        assertEquals(CollectionJobStatus.SUCCESS, job.status());
        assertEquals(1, job.createdCount());
        assertEquals("old", fixture.raw.saved.get("TEST:1").rawPayload().get("value").asText());
    }

    @Test // TS-COLLECT-002, TS-COLLECT-003
    void same_external_id_and_hash_is_unchanged() {
        Fixture fixture = fixture(pages(record("1", "same"), record("1", "same")), true, null);
        assertEquals(1, fixture.service.run("TEST").createdCount());
        CollectionJob second = fixture.service.run("TEST");
        assertEquals(1, second.unchangedCount());
        assertEquals(0, second.updatedCount());
    }

    @Test // TS-COLLECT-004
    void changed_hash_updates_existing_record() {
        Fixture fixture = fixture(pages(record("1", "before"), record("1", "after")), true, null);
        fixture.service.run("TEST");
        CollectionJob second = fixture.service.run("TEST");
        assertEquals(1, second.updatedCount());
        assertEquals("after", fixture.raw.saved.get("TEST:1").rawPayload().get("value").asText());
    }

    @Test // TS-COLLECT-005
    void one_parsing_failure_does_not_fail_batch() {
        CollectionPage page = new CollectionPage(List.of(
            new CollectedRecord.Invalid("bad date"), record("2", "ok")));
        Fixture fixture = fixture(List.of(page), true, null);
        CollectionJob job = fixture.service.run("TEST");
        assertEquals(CollectionJobStatus.PARTIAL_SUCCESS, job.status());
        assertEquals(1, job.failedCount());
        assertEquals(1, job.createdCount());
    }

    @Test // TS-COLLECT-006
    void network_failure_is_retried() {
        FakeLocalContentSource source = new FakeLocalContentSource("TEST",
            List.of(new CollectionPage(List.of(record("1", "ok")))),
            List.of(new SourceCollectionException.Network("timeout"),
                new SourceCollectionException.HttpServer(503, "unavailable")));
        Fixture fixture = fixture(List.of(), true, source);
        assertEquals(CollectionJobStatus.SUCCESS, fixture.service.run("TEST").status());
        assertEquals(3, source.collectCalls());
    }

    @Test
    void non_retryable_failure_is_not_retried() {
        FakeLocalContentSource source = new FakeLocalContentSource("TEST", List.of(),
            List.of(new SourceCollectionException.NonRetryable("400")));
        Fixture fixture = fixture(List.of(), true, source);
        assertEquals(CollectionJobStatus.FAILED, fixture.service.run("TEST").status());
        assertEquals(1, source.collectCalls());
    }

    @Test // TS-COLLECT-007
    void duplicate_external_id_in_batch_is_isolated() {
        Fixture fixture = fixture(List.of(new CollectionPage(List.of(
            record("1", "first"), record("1", "second")))), true, null);
        CollectionJob job = fixture.service.run("TEST");
        assertEquals(CollectionJobStatus.PARTIAL_SUCCESS, job.status());
        assertEquals(1, job.createdCount());
        assertEquals(1, job.failedCount());
    }

    @Test // TS-COLLECT-008
    void disabled_source_cannot_run() {
        Fixture fixture = fixture(List.of(), false, null);
        assertThrows(SourceDisabledException.class, () -> fixture.service.run("TEST"));
        assertTrue(fixture.jobs.saved.isEmpty());
    }

    private List<CollectionPage> pages(CollectedRecord.Valid... records) {
        return java.util.Arrays.stream(records).map(item -> new CollectionPage(List.of(item))).toList();
    }

    private CollectedRecord.Valid record(String id, String value) {
        return new CollectedRecord.Valid(id, mapper.createObjectNode().put("value", value));
    }

    private Fixture fixture(List<CollectionPage> pages, boolean enabled, FakeLocalContentSource suppliedSource) {
        InMemoryDataSources dataSources = new InMemoryDataSources(enabled);
        InMemoryJobs jobs = new InMemoryJobs();
        InMemoryRawRecords raw = new InMemoryRawRecords();
        FakeLocalContentSource source = suppliedSource == null
            ? new FakeLocalContentSource("TEST", pages) : suppliedSource;
        CollectLocalContentService service = new CollectLocalContentService(List.of(source), dataSources,
            jobs, raw, mapper, clock, 3, 0);
        return new Fixture(service, jobs, raw);
    }

    private record Fixture(CollectLocalContentService service, InMemoryJobs jobs, InMemoryRawRecords raw) {
    }

    private static final class InMemoryDataSources implements DataSourceRepository {
        private final boolean enabled;

        private InMemoryDataSources(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public Optional<DataSource> findBySourceCode(String sourceCode) {
            return Optional.of(new DataSource(null, sourceCode, "Test", "Test", "PUBLIC_API",
                null, "PUBLIC", true, true, true, false, "DAILY", enabled));
        }
    }

    private static final class InMemoryJobs implements CollectionJobRepository {
        private final List<CollectionJob> saved = new ArrayList<>();

        @Override
        public CollectionJob save(CollectionJob job) {
            CollectionJob result = job.id() == null ? job.withId((long) saved.size() + 1) : job;
            saved.add(result);
            return result;
        }
    }

    private static final class InMemoryRawRecords implements RawRecordRepository {
        private final Map<String, RawRecord> saved = new HashMap<>();

        @Override
        public RawRecordSaveResult saveIfChanged(RawRecord record) {
            String key = record.sourceCode() + ":" + record.externalId();
            RawRecord old = saved.get(key);
            if (old == null) {
                saved.put(key, record);
                return RawRecordSaveResult.CREATED;
            }
            if (old.contentHash().equals(record.contentHash())) {
                return RawRecordSaveResult.UNCHANGED;
            }
            saved.put(key, record.withId(old.id()));
            return RawRecordSaveResult.UPDATED;
        }
    }
}
