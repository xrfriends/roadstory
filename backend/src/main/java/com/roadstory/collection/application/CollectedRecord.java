package com.roadstory.collection.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Instant;

public sealed interface CollectedRecord permits CollectedRecord.Valid, CollectedRecord.Invalid {
    record Valid(String externalId, Instant sourceUpdatedAt, URI sourceUrl, JsonNode rawPayload)
        implements CollectedRecord {
        public Valid(String externalId, JsonNode rawPayload) {
            this(externalId, null, null, rawPayload);
        }
    }

    record Invalid(String reason) implements CollectedRecord {
    }
}
