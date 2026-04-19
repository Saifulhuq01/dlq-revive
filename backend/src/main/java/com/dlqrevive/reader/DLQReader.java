package com.dlqrevive.reader;

import com.dlqrevive.connector.KafkaConnector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DLQReader — Paginated, memory-safe DLQ message reader.
 *
 * ARCHITECTURAL RULES:
 * - Max page size: 100 messages. NEVER load all messages.
 * - Uses assign() + seek(). NEVER subscribe().
 * - NEVER commits offsets. This is read-only browsing.
 * - Consumer is ALWAYS closed in try-finally.
 */
@Service
public class DLQReader {

    private static final Logger log = LoggerFactory.getLogger(DLQReader.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(2000);

    private final KafkaConnector kafkaConnector;

    public DLQReader(KafkaConnector kafkaConnector) {
        this.kafkaConnector = kafkaConnector;
    }

    /**
     * Reads a paginated batch of messages from a DLQ topic.
     *
     * @param bootstrapServers Kafka broker addresses
     * @param topic            DLQ topic name
     * @param partition        Partition to read from
     * @param fromOffset       Starting offset (inclusive)
     * @param limit            Number of messages to fetch (capped at 100)
     * @return List of DLQ messages with metadata
     */
    public List<DLQMessage> readMessages(String bootstrapServers, String topic,
                                          int partition, long fromOffset, int limit) {
        // Hard cap at MAX_PAGE_SIZE — NEVER allow unlimited reads
        int effectiveLimit = Math.min(limit, MAX_PAGE_SIZE);
        List<DLQMessage> messages = new ArrayList<>(effectiveLimit);

        KafkaConsumer<String, String> consumer = kafkaConnector.createReadOnlyConsumer(
                bootstrapServers, topic + "-" + partition);

        try {
            kafkaConnector.assignAndSeek(consumer, topic, partition, fromOffset);

            int fetched = 0;
            int emptyPolls = 0;

            while (fetched < effectiveLimit && emptyPolls < 3) {
                ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);

                if (records.isEmpty()) {
                    emptyPolls++;
                    continue;
                }

                for (ConsumerRecord<String, String> record : records) {
                    if (fetched >= effectiveLimit) break;

                    messages.add(DLQMessage.builder()
                            .topic(record.topic())
                            .partition(record.partition())
                            .offset(record.offset())
                            .key(record.key())
                            .value(record.value())
                            .timestamp(record.timestamp())
                            .headers(extractHeaders(record))
                            .build());
                    fetched++;
                }
            }

            // NEVER call commitSync() or commitAsync() here
            // This is view-only mode — offsets are NOT manipulated

            log.info("Read {} messages from {}:{} starting at offset {}",
                    messages.size(), topic, partition, fromOffset);

        } finally {
            // ALWAYS close the consumer — no resource leaks
            consumer.close();
            log.debug("Closed consumer for {}:{}", topic, partition);
        }

        return messages;
    }

    private java.util.Map<String, String> extractHeaders(ConsumerRecord<String, String> record) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        record.headers().forEach(header ->
                headers.put(header.key(), new String(header.value(), java.nio.charset.StandardCharsets.UTF_8)));
        return headers;
    }
}
