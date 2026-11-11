package com.roadstory.collection.infrastructure.source;

import com.roadstory.collection.application.CollectionCursor;
import com.roadstory.collection.application.CollectionPage;
import com.roadstory.collection.application.LocalContentSource;
import com.roadstory.collection.application.SourceCollectionException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;

/** Deterministic source for tests and local development; it never calls an external API. */
public final class FakeLocalContentSource implements LocalContentSource {
    private final String sourceCode;
    private final ArrayDeque<CollectionPage> pages;
    private final ArrayDeque<SourceCollectionException> failures;
    private int collectCalls;

    public FakeLocalContentSource(String sourceCode, List<CollectionPage> pages) {
        this(sourceCode, pages, List.of());
    }

    public FakeLocalContentSource(String sourceCode, List<CollectionPage> pages,
                                  Collection<SourceCollectionException> failures) {
        this.sourceCode = sourceCode;
        this.pages = new ArrayDeque<>(pages);
        this.failures = new ArrayDeque<>(failures);
    }

    @Override
    public String sourceCode() {
        return sourceCode;
    }

    @Override
    public CollectionPage collect(CollectionCursor cursor) {
        collectCalls++;
        if (!failures.isEmpty()) {
            throw failures.removeFirst();
        }
        return pages.isEmpty() ? new CollectionPage(List.of()) : pages.removeFirst();
    }

    public int collectCalls() {
        return collectCalls;
    }
}
