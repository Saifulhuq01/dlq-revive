package com.dlqrevive.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dlq/audit")
@CrossOrigin(origins = "http://localhost:4200")
public class AuditController {

    private final JdbcTemplate jdbcTemplate;

    public AuditController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record AuditEntry(long id, String action, String topic, int messageCount,
                             String user, String timestamp, String sessionId) {}

    @GetMapping
    public List<AuditEntry> getAuditTrail(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String action) {
        
        StringBuilder sql = new StringBuilder(
                "SELECT id, action, topic, message_count, \"user\", timestamp, session_id " +
                "FROM audit_trail WHERE 1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (topic != null && !topic.isBlank()) {
            sql.append(" AND topic = ?");
            params.add(topic);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            params.add(action);
        }
        sql.append(" ORDER BY timestamp DESC LIMIT 100");

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new AuditEntry(
                        rs.getLong("id"),
                        rs.getString("action"),
                        rs.getString("topic"),
                        rs.getInt("message_count"),
                        rs.getString("user"),
                        rs.getString("timestamp"),
                        rs.getString("session_id")
                ), params.toArray());
    }
}
