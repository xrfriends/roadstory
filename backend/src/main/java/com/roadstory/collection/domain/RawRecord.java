package com.roadstory.collection.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Instant;

public record RawRecord(Long id, String sourceCode, String externalId, Instant collectedAt,
                        Instant sourceUpdatedAt, String contentHash, URI sourceUrl, JsonNode rawPayload) {
    public RawRecord withId(Long savedId) {
        return new RawRecord(savedId, sourceCode, externalId, collectedAt, sourceUpdatedAt,
            contentHash, sourceUrl, rawPayload);
    }
}
