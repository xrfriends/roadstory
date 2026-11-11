package com.roadstory.collection.infrastructure.persistence;

import com.roadstory.collection.application.CollectionPorts.CollectionJobRepository;
import com.roadstory.collection.application.CollectionPorts.DataSourceRepository;
import com.roadstory.collection.application.CollectionPorts.RawRecordRepository;
import com.roadstory.collection.application.CollectionPorts.RawRecordSaveResult;
import com.roadstory.collection.domain.CollectionJob;
import com.roadstory.collection.domain.DataSource;
import com.roadstory.collection.domain.RawRecord;
import java.net.URI;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!local")
class JpaDataSourceRepository implements DataSourceRepository {
    private final DataSourceJpaRepository repository;

    JpaDataSourceRepository(DataSourceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DataSource> findBySourceCode(String sourceCode) {
        return repository.findBySourceCode(sourceCode).map(entity -> new DataSource(entity.id,
            entity.sourceCode, entity.name, entity.provider, entity.sourceType,
            entity.baseUrl == null ? null : URI.create(entity.baseUrl), entity.licenseType,
            entity.commercialUseAllowed, entity.derivativeAllowed, entity.attributionRequired,
            entity.apiKeyRequired, entity.updateCycle, entity.enabled));
    }
}

@Repository
@Profile("!local")
class JpaCollectionJobRepository implements CollectionJobRepository {
    private final CollectionJobJpaRepository repository;

    JpaCollectionJobRepository(CollectionJobJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CollectionJob save(CollectionJob job) {
        CollectionJobEntity saved = repository.save(new CollectionJobEntity(job.id(), job.sourceCode(),
            job.startedAt(), job.completedAt(), job.status(), job.requestedCount(), job.receivedCount(),
            job.createdCount(), job.updatedCount(), job.unchangedCount(), job.failedCount()));
        return job.withId(saved.id);
    }
}

@Repository
@Profile("!local")
class JpaRawRecordRepository implements RawRecordRepository {
    private final RawRecordJpaRepository repository;

    JpaRawRecordRepository(RawRecordJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RawRecordSaveResult saveIfChanged(RawRecord record) {
        Optional<RawRecordEntity> found = repository.findBySourceCodeAndExternalId(
            record.sourceCode(), record.externalId());
        if (found.isEmpty()) {
            repository.saveAndFlush(toEntity(record));
            return RawRecordSaveResult.CREATED;
        }
        RawRecordEntity existing = found.get();
        if (existing.contentHash.equals(record.contentHash())) {
            return RawRecordSaveResult.UNCHANGED;
        }
        existing.collectedAt = record.collectedAt();
        existing.sourceUpdatedAt = record.sourceUpdatedAt();
        existing.contentHash = record.contentHash();
        existing.sourceUrl = record.sourceUrl() == null ? null : record.sourceUrl().toString();
        existing.rawPayload = record.rawPayload().deepCopy();
        repository.save(existing);
        return RawRecordSaveResult.UPDATED;
    }

    private RawRecordEntity toEntity(RawRecord record) {
        return new RawRecordEntity(record.id(), record.sourceCode(), record.externalId(),
            record.collectedAt(), record.sourceUpdatedAt(), record.contentHash(),
            record.sourceUrl() == null ? null : record.sourceUrl().toString(),
            record.rawPayload().deepCopy());
    }
}
