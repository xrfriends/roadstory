package com.roadstory.collection.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.roadstory.collection.domain.CollectionJobStatus;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "data_source")
class DataSourceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "source_code", nullable = false, unique = true) String sourceCode;
    @Column(nullable = false) String name;
    @Column(nullable = false) String provider;
    @Column(name = "source_type", nullable = false) String sourceType;
    @Column(name = "base_url") String baseUrl;
    @Column(name = "license_type", nullable = false) String licenseType;
    @Column(name = "commercial_use_allowed", nullable = false) boolean commercialUseAllowed;
    @Column(name = "derivative_allowed", nullable = false) boolean derivativeAllowed;
    @Column(name = "attribution_required", nullable = false) boolean attributionRequired;
    @Column(name = "api_key_required", nullable = false) boolean apiKeyRequired;
    @Column(name = "update_cycle", nullable = false) String updateCycle;
    @Column(nullable = false) boolean enabled;

    protected DataSourceEntity() {
    }
}

@Entity
@Table(name = "collection_job")
class CollectionJobEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "source_code", nullable = false) String sourceCode;
    @Column(name = "started_at", nullable = false) Instant startedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) CollectionJobStatus status;
    @Column(name = "requested_count", nullable = false) int requestedCount;
    @Column(name = "received_count", nullable = false) int receivedCount;
    @Column(name = "created_count", nullable = false) int createdCount;
    @Column(name = "updated_count", nullable = false) int updatedCount;
    @Column(name = "unchanged_count", nullable = false) int unchangedCount;
    @Column(name = "failed_count", nullable = false) int failedCount;

    protected CollectionJobEntity() {
    }

    CollectionJobEntity(Long id, String sourceCode, Instant startedAt, Instant completedAt,
                        CollectionJobStatus status, int requestedCount, int receivedCount,
                        int createdCount, int updatedCount, int unchangedCount, int failedCount) {
        this.id = id;
        this.sourceCode = sourceCode;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.status = status;
        this.requestedCount = requestedCount;
        this.receivedCount = receivedCount;
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.unchangedCount = unchangedCount;
        this.failedCount = failedCount;
    }
}

@Entity
@Table(name = "raw_record", uniqueConstraints = @UniqueConstraint(
    name = "uq_raw_record_source_external", columnNames = {"source_code", "external_id"}))
class RawRecordEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "source_code", nullable = false) String sourceCode;
    @Column(name = "external_id", nullable = false) String externalId;
    @Column(name = "collected_at", nullable = false) Instant collectedAt;
    @Column(name = "source_updated_at") Instant sourceUpdatedAt;
    @Column(name = "content_hash", nullable = false, length = 64) String contentHash;
    @Column(name = "source_url") String sourceUrl;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb") JsonNode rawPayload;

    protected RawRecordEntity() {
    }

    RawRecordEntity(Long id, String sourceCode, String externalId, Instant collectedAt,
                    Instant sourceUpdatedAt, String contentHash, String sourceUrl, JsonNode rawPayload) {
        this.id = id;
        this.sourceCode = sourceCode;
        this.externalId = externalId;
        this.collectedAt = collectedAt;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.contentHash = contentHash;
        this.sourceUrl = sourceUrl;
        this.rawPayload = rawPayload;
    }
}
