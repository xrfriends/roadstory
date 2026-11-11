package com.roadstory.collection.application;

public class SourceCollectionException extends RuntimeException {
    public SourceCollectionException(String message) {
        super(message);
    }

    public SourceCollectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public static final class Network extends SourceCollectionException {
        public Network(String message) {
            super(message);
        }
    }

    public static final class HttpServer extends SourceCollectionException {
        private final int status;

        public HttpServer(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    public static final class NonRetryable extends SourceCollectionException {
        public NonRetryable(String message) {
            super(message);
        }
    }
}
