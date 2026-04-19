package com.dlqrevive.redrive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * IdempotencyGuard — Prevents double-redrive of messages.
 *
 * ARCHITECTURAL RULE:
 * Before producing ANY message during redrive:
 *   SELECT 1 FROM redrive_log WHERE topic=? AND partition=? AND offset=?
 *   If found → skip that message.
 *   If not found → INSERT then produce.
 *
 * The UNIQUE constraint on (topic, partition, offset) provides the final safety net.
 * This gives exactly-once redrive semantics without Kafka transactions complexity.
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Checks if a message has already been redriven.
     *
     * @param topic     Source DLQ topic
     * @param partition Source partition
     * @param offset    Source offset
     * @return true if this message was already redriven (should be SKIPPED)
     */
    public boolean alreadyRedriven(String topic, int partition, long offset) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM redrive_log WHERE topic = ? AND partition = ? AND \"offset\" = ?",
                Integer.class, topic, partition, offset);
        return count != null && count > 0;
    }

    /**
     * Records a message as redriven. MUST be called BEFORE producing the message.
     * The UNIQUE constraint on (topic, partition, offset) ensures idempotency
     * even in concurrent scenarios.
     *
     * @param topic      Source DLQ topic
     * @param partition  Source partition
     * @param offset     Source offset
     * @param redrivenBy User who initiated the redrive
     */
    @Transactional
    public void recordRedrive(String topic, int partition, long offset, String redrivenBy) {
        jdbcTemplate.update(
                "INSERT INTO redrive_log (topic, partition, \"offset\", redriven_at, redriven_by) " +
                "VALUES (?, ?, ?, ?, ?)",
                topic, partition, offset, LocalDateTime.now(), redrivenBy);
        log.debug("Recorded redrive: {}:{}:{} by {}", topic, partition, offset, redrivenBy);
    }
}
