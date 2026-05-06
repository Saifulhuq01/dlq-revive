package com.dlqrevive.redrive;

import com.dlqrevive.audit.AuditLogger;
import com.dlqrevive.transform.JsonataEngine;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RedriveEngine — Safely replays transformed DLQ messages to the target topic.
 *
 * ARCHITECTURAL RULES:
 * - FREE TIER: Max 100 messages per redrive session. Enforced HERE, not in controller.
 * - IDEMPOTENCY: Every message checked against redrive_log before producing.
 *   INSERT log record BEFORE producing. UNIQUE constraint prevents duplicates.
 * - All operations are transactional.
 */
@Service
public class RedriveEngine {

    private static final Logger log = LoggerFactory.getLogger(RedriveEngine.class);
    private static final int FREE_TIER_MAX_MESSAGES = 100;

    private final IdempotencyGuard idempotencyGuard;
    private final JsonataEngine jsonataEngine;
    private final AuditLogger auditLogger;

    public RedriveEngine(IdempotencyGuard idempotencyGuard, JsonataEngine jsonataEngine,
                         AuditLogger auditLogger) {
        this.idempotencyGuard = idempotencyGuard;
        this.jsonataEngine = jsonataEngine;
        this.auditLogger = auditLogger;
    }

    /**
     * Executes a redrive session: transforms and produces messages to the target topic.
     *
     * @param request The redrive request containing source messages, target topic, and transform expression
     * @return Summary of the redrive operation
     */
    @Transactional
    public RedriveSummary executeRedrive(RedriveRequest request) {
        // FREE TIER ENFORCEMENT — enforced in service layer, NOT controller
        if (request.getMessages().size() > FREE_TIER_MAX_MESSAGES) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Free tier limit: max " + FREE_TIER_MAX_MESSAGES + " messages per redrive session. " +
                    "Upgrade to Team tier for unlimited bulk redrive. → https://dlqrevive.com/pricing");
        }

        int produced = 0;
        int skipped = 0;
        int failed = 0;

        KafkaProducer<String, String> producer = createProducer(request.getBootstrapServers());

        try {
            for (RedriveMessage msg : request.getMessages()) {
                // IDEMPOTENCY CHECK — prevents double-redrive on pod restart
                if (idempotencyGuard.alreadyRedriven(msg.getTopic(), msg.getPartition(), msg.getOffset())) {
                    log.info("Skipping already-redriven message: {}:{}:{}",
                            msg.getTopic(), msg.getPartition(), msg.getOffset());
                    skipped++;
                    continue;
                }

                try {
                    // Transform the message using JSONata (if expression provided)
                    String transformedValue = msg.getValue();
                    if (request.getExpression() != null && !request.getExpression().isBlank()) {
                        transformedValue = jsonataEngine.transform(msg.getValue(), request.getExpression());
                    }

                    // LOG BEFORE PRODUCING — critical for idempotency
                    idempotencyGuard.recordRedrive(msg.getTopic(), msg.getPartition(),
                            msg.getOffset(), request.getUser());

                    // Produce to target topic
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                            request.getTargetTopic(), msg.getKey(), transformedValue);
                    producer.send(record).get(); // Synchronous send for safety in fintech

                    produced++;
                    log.debug("Redrove message {}:{}:{} → {}",
                            msg.getTopic(), msg.getPartition(), msg.getOffset(), request.getTargetTopic());

                } catch (Exception e) {
                    log.error("Failed to redrive message {}:{}:{}: {}",
                            msg.getTopic(), msg.getPartition(), msg.getOffset(), e.getMessage());
                    failed++;
                }
            }
        } finally {
            producer.close();
        }

        // Audit log the entire session
        auditLogger.logRedrive(request.getTargetTopic(), produced, skipped, failed,
                request.getUser(), request.getSessionId());

        RedriveSummary summary = RedriveSummary.builder()
                .produced(produced)
                .skipped(skipped)
                .failed(failed)
                .targetTopic(request.getTargetTopic())
                .sessionId(request.getSessionId())
                .build();

        log.info("Redrive complete: {}", summary);
        return summary;
    }

    private KafkaProducer<String, String> createProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // Strongest durability
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        return new KafkaProducer<>(props);
    }

    public SseEmitter executeRedriveStream(RedriveRequest request) {
        if (request.getMessages().size() > FREE_TIER_MAX_MESSAGES) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Free tier limit: max " + FREE_TIER_MAX_MESSAGES + " messages per redrive session.");
        }

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        AtomicBoolean isRunning = new AtomicBoolean(true);

        emitter.onCompletion(() -> isRunning.set(false));
        emitter.onTimeout(() -> isRunning.set(false));
        emitter.onError(e -> isRunning.set(false));

        new Thread(() -> {
            int produced = 0;
            int skipped = 0;
            int failed = 0;

            KafkaProducer<String, String> producer = createProducer(request.getBootstrapServers());

            try {
                for (RedriveMessage msg : request.getMessages()) {
                    if (!isRunning.get()) break;

                    if (idempotencyGuard.alreadyRedriven(msg.getTopic(), msg.getPartition(), msg.getOffset())) {
                        skipped++;
                        emitProgress(emitter, produced, skipped, failed, "SKIPPED", msg);
                        continue;
                    }

                    try {
                        String transformedValue = msg.getValue();
                        if (request.getExpression() != null && !request.getExpression().isBlank()) {
                            transformedValue = jsonataEngine.transform(msg.getValue(), request.getExpression());
                        }

                        idempotencyGuard.recordRedrive(msg.getTopic(), msg.getPartition(),
                                msg.getOffset(), request.getUser());

                        ProducerRecord<String, String> record = new ProducerRecord<>(
                                request.getTargetTopic(), msg.getKey(), transformedValue);
                        producer.send(record).get();

                        produced++;
                        emitProgress(emitter, produced, skipped, failed, "PRODUCED", msg);
                        // Add a small delay for demo purposes to see the live events
                        Thread.sleep(150);

                    } catch (Exception e) {
                        failed++;
                        emitProgress(emitter, produced, skipped, failed, "FAILED", msg);
                    }
                }

                auditLogger.logRedrive(request.getTargetTopic(), produced, skipped, failed,
                        request.getUser(), request.getSessionId());

                RedriveSummary summary = RedriveSummary.builder()
                        .produced(produced)
                        .skipped(skipped)
                        .failed(failed)
                        .targetTopic(request.getTargetTopic())
                        .sessionId(request.getSessionId())
                        .build();

                emitter.send(SseEmitter.event().name("complete").data(summary));
                emitter.complete();

            } catch (Exception e) {
                log.error("Redrive stream failed", e);
                emitter.completeWithError(e);
            } finally {
                producer.close();
            }
        }).start();

        return emitter;
    }

    private void emitProgress(SseEmitter emitter, int produced, int skipped, int failed, String status, RedriveMessage msg) throws Exception {
        RedriveProgressEvent event = new RedriveProgressEvent(produced, skipped, failed, status, msg.getOffset());
        emitter.send(SseEmitter.event().name("progress").data(event));
    }

    public record RedriveProgressEvent(int produced, int skipped, int failed, String status, long offset) {}
}
