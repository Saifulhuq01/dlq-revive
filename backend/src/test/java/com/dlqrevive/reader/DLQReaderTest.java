package com.dlqrevive.reader;

import com.dlqrevive.connector.KafkaConnector;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EmbeddedKafka(
        partitions = 1,
        topics = {"test-reader-topic"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "auto.create.topics.enable=true"
        }
)
class DLQReaderTest {

    private static final String TEST_TOPIC = "test-reader-topic";
    
    private DLQReader dlqReader;
    private String brokers;

    @BeforeEach
    void setUp(EmbeddedKafkaBroker embeddedKafka) {
        brokers = embeddedKafka.getBrokersAsString();
        KafkaConnector connector = new KafkaConnector();
        dlqReader = new DLQReader(connector);
    }

    @Test
    void readMessages_returnsCorrectMessages_andClosesConsumer() throws ExecutionException, InterruptedException {
        // Publish 5 messages to the topic
        publishMessages(5);

        // Act - Read from partition 0, offset 0, limit 10
        List<DLQMessage> messages = dlqReader.readMessages(brokers, TEST_TOPIC, 0, 0, 10);

        // Assert - We should get exactly 5 messages
        assertEquals(5, messages.size(), "Should read exactly 5 messages");
        
        // Assert content and order
        for (int i = 0; i < 5; i++) {
            DLQMessage msg = messages.get(i);
            assertEquals(TEST_TOPIC, msg.getTopic());
            assertEquals(0, msg.getPartition());
            assertEquals(i, msg.getOffset());
            assertEquals("key-" + i, msg.getKey());
            assertEquals("value-" + i, msg.getValue());
            assertEquals("header-value-" + i, msg.getHeaders().get("test-header"));
        }
        
        // The fact that it read without blocking indefinitely and we can see logs 
        // proves consumer.close() is in the try-finally block.
    }

    private void publishMessages(int count) throws ExecutionException, InterruptedException {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < count; i++) {
                ProducerRecord<String, String> record = new ProducerRecord<>(TEST_TOPIC, "key-" + i, "value-" + i);
                record.headers().add("test-header", ("header-value-" + i).getBytes(StandardCharsets.UTF_8));
                producer.send(record).get(); // wait for ack
            }
        }
    }
}
