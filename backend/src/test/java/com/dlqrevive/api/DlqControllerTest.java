package com.dlqrevive.api;

import com.dlqrevive.audit.AuditLogger;
import com.dlqrevive.reader.DLQMessage;
import com.dlqrevive.reader.DLQReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API contract tests for DlqController using Spring MockMvc.
 *
 * These validate the REST contract between the Angular frontend and Spring Boot backend.
 * Bugs here mean the frontend breaks silently — the user sees a blank screen or
 * cryptic error messages instead of actionable feedback.
 *
 * Production bugs these tests catch:
 * - Frontend can't parse response → blank screen
 * - Missing required params → NPE → 500 instead of 400
 * - CORS misconfiguration → Angular can't reach backend at all
 * - Audit logging called on every request (compliance requirement)
 */
@WebMvcTest(DlqController.class)
class DlqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DLQReader dlqReader;

    @MockBean
    private AuditLogger auditLogger;

    // ===== HAPPY PATH TESTS =====

    @Nested
    @DisplayName("Happy Path — Valid Requests")
    class HappyPath {

        @Test
        @DisplayName("GET /dlq/{topic}/messages with valid params returns 200 + JSON")
        void getMessages_validRequest_returns200WithJson() throws Exception {
            // Arrange — Mock DLQReader to return one message
            DLQMessage msg = DLQMessage.builder()
                    .topic("payments.dlq")
                    .partition(0)
                    .offset(0)
                    .key("key-0")
                    .value("{\"orderId\":\"ORD-1\",\"amount\":500}")
                    .timestamp(System.currentTimeMillis())
                    .headers(Map.of("error-reason", "Schema mismatch"))
                    .build();

            when(dlqReader.readMessages(eq("localhost:9092"), eq("payments.dlq"), eq(0), eq(0L), eq(10)))
                    .thenReturn(List.of(msg));

            // Act + Assert
            mockMvc.perform(get("/dlq/payments.dlq/messages")
                            .param("bootstrapServers", "localhost:9092")
                            .param("partition", "0")
                            .param("fromOffset", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$[0].topic").value("payments.dlq"))
                    .andExpect(jsonPath("$[0].key").value("key-0"))
                    .andExpect(jsonPath("$[0].offset").value(0))
                    .andExpect(jsonPath("$[0].value").value("{\"orderId\":\"ORD-1\",\"amount\":500}"));
        }

        @Test
        @DisplayName("GET /dlq/{topic}/messages uses defaults for optional params")
        void getMessages_optionalParamsDefault_usesDefaults() throws Exception {
            // Only bootstrapServers is required — partition, fromOffset, limit have defaults
            when(dlqReader.readMessages(eq("localhost:9092"), eq("payments.dlq"), eq(0), eq(0L), eq(10)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/dlq/payments.dlq/messages")
                            .param("bootstrapServers", "localhost:9092"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Empty topic returns empty array, not null or error")
        void getMessages_emptyTopic_returnsEmptyArray() throws Exception {
            when(dlqReader.readMessages(anyString(), anyString(), anyInt(), anyLong(), anyInt()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/dlq/empty.dlq/messages")
                            .param("bootstrapServers", "localhost:9092"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }
    }

    // ===== INPUT VALIDATION TESTS =====

    @Nested
    @DisplayName("Input Validation — Must return 400, NEVER 500")
    class InputValidation {

        @Test
        @DisplayName("Missing bootstrapServers param returns 400 Bad Request")
        void getMessages_missingBootstrapServers_returns400() throws Exception {
            // bootstrapServers is @RequestParam without defaultValue — it's required.
            // If the frontend is misconfigured and doesn't send it, we need 400 not 500.
            mockMvc.perform(get("/dlq/payments.dlq/messages"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== AUDIT LOGGING TESTS =====

    @Nested
    @DisplayName("Audit Logging — Compliance Requirement")
    class AuditLogging {

        @Test
        @DisplayName("Every browse request triggers audit log — required for compliance")
        void getMessages_validRequest_callsAuditLogger() throws Exception {
            // From Blueprint v2: "Every action logged to PostgreSQL with full traceability"
            // AuditLogger.logBrowse() MUST be called on every request.
            // If this test fails, the audit trail has gaps → compliance violation in fintech.
            when(dlqReader.readMessages(anyString(), anyString(), anyInt(), anyLong(), anyInt()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/dlq/payments.dlq/messages")
                            .param("bootstrapServers", "localhost:9092")
                            .param("partition", "2")
                            .param("fromOffset", "100"));

            verify(auditLogger, times(1)).logBrowse(
                    eq("payments.dlq"),
                    eq(2),
                    eq(100L),
                    eq("api-user")
            );
        }
    }

    // ===== RESPONSE FORMAT TESTS =====

    @Nested
    @DisplayName("Response Format — Angular frontend depends on this contract")
    class ResponseFormat {

        @Test
        @DisplayName("Response includes all required fields for Angular mat-table")
        void getMessages_responseFormat_hasAllRequiredFields() throws Exception {
            // The Angular BrowseComponent expects these exact field names.
            // If we rename any field, the mat-table shows undefined.
            DLQMessage msg = DLQMessage.builder()
                    .topic("payments.dlq")
                    .partition(0)
                    .offset(42)
                    .key("payment-key")
                    .value("{\"data\":\"test\"}")
                    .timestamp(1714000000000L)
                    .headers(Map.of())
                    .build();

            when(dlqReader.readMessages(anyString(), anyString(), anyInt(), anyLong(), anyInt()))
                    .thenReturn(List.of(msg));

            mockMvc.perform(get("/dlq/payments.dlq/messages")
                            .param("bootstrapServers", "localhost:9092"))
                    .andExpect(jsonPath("$[0].topic").exists())
                    .andExpect(jsonPath("$[0].partition").exists())
                    .andExpect(jsonPath("$[0].offset").exists())
                    .andExpect(jsonPath("$[0].key").exists())
                    .andExpect(jsonPath("$[0].value").exists())
                    .andExpect(jsonPath("$[0].timestamp").exists())
                    .andExpect(jsonPath("$[0].headers").exists());
        }
    }
}
