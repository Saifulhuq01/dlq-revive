package com.dlqrevive.reader;

import lombok.Builder;
import lombok.Data;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

    public static DLQMessage from(ConsumerRecord<String, String> record) {
        Map<String, String> headersMap = new LinkedHashMap<>();
        if (record.headers() != null) {
            record.headers().forEach(header ->
                    headersMap.put(header.key(), new String(header.value(), StandardCharsets.UTF_8)));
        }

        return DLQMessage.builder()
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .key(record.key())
                .value(record.value())
                .timestamp(record.timestamp())
                .headers(headersMap)
                .build();
    }
}
