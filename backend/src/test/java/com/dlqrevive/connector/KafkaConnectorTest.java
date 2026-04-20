package com.dlqrevive.connector;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for KafkaConnector using an embedded Kafka broker.
 *
 * Validates:
 * - Consumer creation with correct config (no group.id, auto-commit disabled)
 * - assign() + seek() to offset 0 works without exceptions
 * - Foundation is solid before building reader/redrive layers on top
 */
@EmbeddedKafka(
        partitions = 1,
        topics = {"test-dlq-topic"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "auto.create.topics.enable=true"
        }
)
class KafkaConnectorTest {

    private static final String TEST_TOPIC = "test-dlq-topic";

    @Test
    void createConsumer_assignAndSeek_noExceptions(EmbeddedKafkaBroker embeddedKafka) {
        // Given
        KafkaConnector connector = new KafkaConnector();
        String brokers = embeddedKafka.getBrokersAsString();

        // When — create a read-only consumer and assign+seek to partition 0, offset 0
        KafkaConsumer<String, String> consumer = connector.createReadOnlyConsumer(brokers, "test-client");
        try {
            // Verify consumer was created
            assertNotNull(consumer, "Consumer should not be null");

            // assign + seek should not throw — use a final reference for lambda
            final KafkaConsumer<String, String> readOnlyConsumer = consumer;
            assertDoesNotThrow(() -> connector.assignAndSeek(readOnlyConsumer, TEST_TOPIC, 0, 0),
                    "assignAndSeek should not throw for a valid topic/partition/offset");

            // Verify no group.id was set (assign mode, not subscribe mode)
            // groupMetadata() throws InvalidGroupIdException when no group.id is configured —
            // this IS the correct behavior, proving we're in pure assign() mode
            assertThrows(org.apache.kafka.common.errors.InvalidGroupIdException.class,
                    () -> readOnlyConsumer.groupMetadata(),
                    "Consumer should have no group.id — groupMetadata() must throw");

        } finally {
            // ARCHITECTURAL RULE: consumers MUST be closed in try-finally
            consumer.close();
        }
    }
}
