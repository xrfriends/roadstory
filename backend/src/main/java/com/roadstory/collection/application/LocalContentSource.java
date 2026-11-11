package com.roadstory.collection.application;

public interface LocalContentSource {
    String sourceCode();

    CollectionPage collect(CollectionCursor cursor);
}
