package com.roadstory.collection.application;

import java.util.List;

public record CollectionPage(List<CollectedRecord> records, int requestedCount, CollectionCursor nextCursor) {
    public CollectionPage {
        records = List.copyOf(records);
    }

    public CollectionPage(List<CollectedRecord> records) {
        this(records, records.size(), null);
    }
}
