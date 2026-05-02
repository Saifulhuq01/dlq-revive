package com.dlqrevive.redrive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotencyGuard unit tests.
 * Validates the UNIQUE constraint logic that prevents double-redrive.
 */
class IdempotencyGuardTest {

    private JdbcTemplate jdbcTemplate;
    private IdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        guard = new IdempotencyGuard(jdbcTemplate);
    }

    @Nested
    @DisplayName("alreadyRedriven()")
    class AlreadyRedriven {

        @Test
        @DisplayName("returns false when message has NOT been redriven")
        void returnsFalse_whenNotRedriven() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                    eq("payments.dlq"), eq(0), eq(42L)))
                    .thenReturn(0);

            boolean result = guard.alreadyRedriven("payments.dlq", 0, 42L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns true when message HAS been redriven")
        void returnsTrue_whenAlreadyRedriven() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                    eq("payments.dlq"), eq(0), eq(42L)))
                    .thenReturn(1);

            boolean result = guard.alreadyRedriven("payments.dlq", 0, 42L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when count is null (edge case)")
        void returnsFalse_whenCountIsNull() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                    eq("payments.dlq"), eq(0), eq(42L)))
                    .thenReturn(null);

            boolean result = guard.alreadyRedriven("payments.dlq", 0, 42L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("recordRedrive()")
    class RecordRedrive {

        @Test
        @DisplayName("inserts a row into redrive_log on first call")
        void recordsRedrive_successfully() {
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
                    .thenReturn(1);

            guard.recordRedrive("payments.dlq", 0, 42L, "api-user");

            verify(jdbcTemplate, times(1)).update(
                    contains("INSERT INTO redrive_log"),
                    eq("payments.dlq"), eq(0), eq(42L),
                    any(), eq("api-user"));
        }

        @Test
        @DisplayName("UNIQUE constraint prevents duplicate — second call throws DuplicateKeyException")
        void secondCall_throwsDueToUniqueConstraint() {
            // First call succeeds
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
                    .thenReturn(1)
                    .thenThrow(new org.springframework.dao.DuplicateKeyException(
                            "duplicate key value violates unique constraint \"redrive_log_topic_partition_offset_key\""));

            // First call — success
            guard.recordRedrive("payments.dlq", 0, 42L, "api-user");

            // Second call — UNIQUE constraint violation
            assertThatThrownBy(() -> guard.recordRedrive("payments.dlq", 0, 42L, "api-user"))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class)
                    .hasMessageContaining("duplicate key");

            verify(jdbcTemplate, times(2)).update(anyString(), any(), any(), any(), any(), any());
        }
    }
}
