package com.dlqrevive.transform;

/**
 * Exception thrown when a JSONata transformation fails.
 */
public class TransformationException extends RuntimeException {

    public TransformationException(String message) {
        super(message);
    }

    public TransformationException(String message, Throwable cause) {
        super(message, cause);
    }
}
