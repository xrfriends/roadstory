package com.roadstory.collection.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface DataSourceJpaRepository extends JpaRepository<DataSourceEntity, Long> {
    Optional<DataSourceEntity> findBySourceCode(String sourceCode);
}

interface CollectionJobJpaRepository extends JpaRepository<CollectionJobEntity, Long> {
}

interface RawRecordJpaRepository extends JpaRepository<RawRecordEntity, Long> {
    Optional<RawRecordEntity> findBySourceCodeAndExternalId(String sourceCode, String externalId);
}
