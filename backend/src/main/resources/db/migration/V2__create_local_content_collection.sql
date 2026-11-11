CREATE TABLE data_source (
    id BIGSERIAL PRIMARY KEY,
    source_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    source_type VARCHAR(100) NOT NULL,
    base_url TEXT,
    license_type VARCHAR(100) NOT NULL,
    commercial_use_allowed BOOLEAN NOT NULL,
    derivative_allowed BOOLEAN NOT NULL,
    attribution_required BOOLEAN NOT NULL,
    api_key_required BOOLEAN NOT NULL,
    update_cycle VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE collection_job (
    id BIGSERIAL PRIMARY KEY,
    source_code VARCHAR(100) NOT NULL REFERENCES data_source(source_code),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED')),
    requested_count INTEGER NOT NULL DEFAULT 0 CHECK (requested_count >= 0),
    received_count INTEGER NOT NULL DEFAULT 0 CHECK (received_count >= 0),
    created_count INTEGER NOT NULL DEFAULT 0 CHECK (created_count >= 0),
    updated_count INTEGER NOT NULL DEFAULT 0 CHECK (updated_count >= 0),
    unchanged_count INTEGER NOT NULL DEFAULT 0 CHECK (unchanged_count >= 0),
    failed_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_count >= 0)
);

CREATE INDEX idx_collection_job_source_started ON collection_job (source_code, started_at DESC);

CREATE TABLE raw_record (
    id BIGSERIAL PRIMARY KEY,
    source_code VARCHAR(100) NOT NULL REFERENCES data_source(source_code),
    external_id VARCHAR(255) NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    source_updated_at TIMESTAMPTZ,
    content_hash CHAR(64) NOT NULL,
    source_url TEXT,
    raw_payload JSONB NOT NULL,
    CONSTRAINT uq_raw_record_source_external UNIQUE (source_code, external_id)
);

CREATE INDEX idx_raw_record_source_collected ON raw_record (source_code, collected_at DESC);
