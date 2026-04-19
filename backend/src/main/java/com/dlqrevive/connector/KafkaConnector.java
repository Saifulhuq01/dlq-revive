package com.dlqrevive.connector;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

/**
 * KafkaConnector — Creates configured KafkaConsumer instances for DLQ reading.
 *
 * ARCHITECTURAL RULES:
 * - Uses assign() + seek() ONLY. NEVER subscribe().
 * - NEVER calls commitSync() or commitAsync() in view mode.
 * - All consumers MUST be closed in try-finally blocks by the caller.
 */
@Component
public class KafkaConnector {

    private static final Logger log = LoggerFactory.getLogger(KafkaConnector.class);

    /**
     * Creates a KafkaConsumer configured for read-only DLQ browsing.
     * The consumer uses assign() mode — it does NOT join any consumer group.
     *
     * @param bootstrapServers Kafka broker addresses (e.g., "localhost:9092")
     * @param clientId         Unique client identifier for this connection
     * @return A configured KafkaConsumer ready for assign()+seek() operations
     */
    public KafkaConsumer<String, String> createReadOnlyConsumer(String bootstrapServers, String clientId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "dlq-revive-" + clientId);
        // No group.id — we use assign() mode, not subscribe()
        // This ensures we never steal partitions from real consumers
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100"); // Hard cap

        log.info("Creating read-only Kafka consumer for brokers: {}", bootstrapServers);
        return new KafkaConsumer<>(props);
    }

    /**
     * Assigns a consumer to a specific topic partition and seeks to the given offset.
     * This is the ONLY correct way to read DLQ messages in view mode.
     *
     * @param consumer  The Kafka consumer (must NOT be subscribed to any group)
     * @param topic     The DLQ topic name
     * @param partition The partition number
     * @param offset    The starting offset to read from
     */
    public void assignAndSeek(KafkaConsumer<String, String> consumer, String topic, int partition, long offset) {
        TopicPartition tp = new TopicPartition(topic, partition);
        consumer.assign(List.of(tp));
        consumer.seek(tp, offset);
        log.debug("Assigned to {}:{} at offset {}", topic, partition, offset);
    }
}
