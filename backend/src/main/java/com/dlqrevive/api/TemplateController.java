package com.dlqrevive.api;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/dlq/templates")
@CrossOrigin(origins = "http://localhost:4200")
public class TemplateController {

    private final JdbcTemplate jdbcTemplate;

    public TemplateController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record TemplateRequest(String name, String expression) {}
    public record TemplateResponse(String id, String name, String expression) {}

    @PostMapping
    public ResponseEntity<?> saveTemplate(@RequestBody TemplateRequest request) {
        String user = "api-user"; // Default user

        // Check if user already has 1 template
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transform_templates WHERE created_by = ?",
                Integer.class, user);

        if (count != null && count >= 1) {
            return ResponseEntity.status(402)
                    .body(Map.of("error", "Free tier limit reached. Please upgrade to save more than 1 template."));
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO transform_templates (id, name, expression, created_by, created_at) VALUES (?, ?, ?, ?, ?)",
                id, request.name(), request.expression(), user, LocalDateTime.now());

        return ResponseEntity.ok(new TemplateResponse(id.toString(), request.name(), request.expression()));
    }

    @GetMapping
    public List<TemplateResponse> getTemplates() {
        String user = "api-user";
        return jdbcTemplate.query(
                "SELECT id, name, expression FROM transform_templates WHERE created_by = ? ORDER BY created_at DESC",
                (rs, rowNum) -> new TemplateResponse(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("expression")
                ), user);
    }
}
