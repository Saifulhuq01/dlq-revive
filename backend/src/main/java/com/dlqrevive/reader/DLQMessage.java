package com.dlqrevive.reader;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Represents a single DLQ message with its Kafka metadata.
 */
@Data
@Builder
public class DLQMessage {
    private String topic;
    private int partition;
    private long offset;
    private String key;
    private String value;
    private long timestamp;
    private Map<String, String> headers;
}
