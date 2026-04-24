package com.dlqrevive.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AuditLogger — Logs every redrive action to PostgreSQL.
 * Every action is traceable: who, what, when, how many messages.
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private final JdbcTemplate jdbcTemplate;

    public AuditLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void logRedrive(String topic, int produced, int skipped, int failed,
                           String user, String sessionId) {
        jdbcTemplate.update(
                "INSERT INTO audit_trail (action, topic, message_count, \"user\", timestamp, session_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                "REDRIVE", topic, produced, user, LocalDateTime.now(), sessionId);

        log.info("Audit: REDRIVE by {} on {} — produced={}, skipped={}, failed={}, session={}",
                user, topic, produced, skipped, failed, sessionId);
    }

    @Transactional
    public void logBrowse(String topic, int partition, long fromOffset, String user) {
        // We use a generated session_id for browse because browsing is stateless at the moment.
        // If we add full session tracking later, we should pass the actual session ID from the request.
        jdbcTemplate.update(
                "INSERT INTO audit_trail (action, topic, message_count, \"user\", timestamp, session_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                "BROWSE", topic, 0, user, LocalDateTime.now(), "browse-" + System.currentTimeMillis());

        log.debug("Audit: BROWSE by {} on {}:{} from offset {}", user, topic, partition, fromOffset);
    }
}
