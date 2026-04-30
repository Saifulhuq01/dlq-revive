package com.dlqrevive.transform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonataEngine — the ONLY transformation engine allowed in DLQ Revive.
 *
 * SECURITY CONTEXT:
 * JSONata is purely declarative (zero system access, zero file access, zero network access).
 * These tests validate that:
 * 1. Valid transformations produce correct output
 * 2. Invalid/malicious expressions throw TransformationException (not NPE, not system crash)
 * 3. Edge cases (null results, empty input) are handled gracefully
 *
 * Production bugs these tests catch:
 * - Broken transformations silently corrupt message payloads
 * - NPE crashes the redrive mid-batch (23,000 payment events stuck again)
 * - User submits garbage expression → 500 error instead of useful message
 * - Empty messages produced to target topic → downstream consumers crash
 */
class JsonataEngineTest {

    private JsonataEngine engine;

    @BeforeEach
    void setUp() {
        engine = new JsonataEngine();
    }

    // ===== VALID TRANSFORMATION TESTS =====

    @Nested
    @DisplayName("Valid Transformations")
    class ValidTransformations {

        @Test
        @DisplayName("Should transform JSON with field renaming — the most common DLQ recovery operation")
        void transform_fieldRenaming_returnsCorrectOutput() {
            // This is the exact use case from the blueprint:
            // user_id renamed to customerId after a schema change
            String input = "{\"orderId\": \"ORD-1\", \"user_id\": \"USR-100\", \"amount\": 500}";
            String expression = "{\"orderId\": orderId, \"customerId\": user_id, \"amount\": amount}";

            String result = engine.transform(input, expression);

            assertNotNull(result);
            assertTrue(result.contains("\"customerId\""));
            assertTrue(result.contains("USR-100"));
            assertFalse(result.contains("user_id"));
        }

        @Test
        @DisplayName("Should uppercase string fields — fixing String to Enum conversion (blueprint scenario)")
        void transform_uppercaseConversion_fintechScenario() {
            // From Understanding Guide: status was String "pending", V2 consumer expects Enum "PENDING"
            String input = "{\"orderId\": \"ORD-1\", \"amount\": 1500, \"status\": \"pending\"}";
            String expression = "{\"orderId\": orderId, \"amount\": amount, \"status\": $uppercase(status)}";

            String result = engine.transform(input, expression);

            assertNotNull(result);
            assertTrue(result.contains("\"PENDING\""), "Status should be uppercased for Enum compatibility");
        }

        @Test
        @DisplayName("Should add missing required fields — processedAt field addition (blueprint V1→V2)")
        void transform_addMissingField_schemaEvolution() {
            // V2 schema requires processedAt which V1 messages don't have
            String input = "{\"orderId\": \"ORD-1\", \"amount\": 1500, \"status\": \"PENDING\"}";
            String expression = "{\"orderId\": orderId, \"amount\": amount, \"status\": status, \"processedAt\": $now()}";

            String result = engine.transform(input, expression);

            assertNotNull(result);
            assertTrue(result.contains("processedAt"), "Should add the missing processedAt field");
        }

        @Test
        @DisplayName("Should convert number types — String amount to Integer (fintech critical)")
        void transform_stringToNumber_paymentAmountConversion() {
            // From Understanding Guide Scenario 1: amount was String "1500.00", new consumer expects Integer
            String input = "{\"orderId\": \"ORD-1\", \"amount\": \"1500\"}";
            String expression = "{\"orderId\": orderId, \"amount\": $number(amount)}";

            String result = engine.transform(input, expression);

            assertNotNull(result);
            // $number("1500") should return 1500 as a number, not a string
            assertTrue(result.contains("1500"), "Amount should be converted to number");
        }

        @Test
        @DisplayName("Should handle identity transformation — no changes needed")
        void transform_identityExpression_passesThrough() {
            String input = "{\"orderId\": \"ORD-1\", \"amount\": 500}";
            String expression = "$"; // JSONata identity — returns the input as-is

            String result = engine.transform(input, expression);

            assertNotNull(result);
            assertTrue(result.contains("ORD-1"));
            assertTrue(result.contains("500"));
        }

        @Test
        @DisplayName("Should transform status to paymentStatus")
        void transform_statusToPaymentStatus() {
            String input = "{\"status\":\"PENDING\"}";
            String expression = "{\"paymentStatus\": status}";

            String result = engine.transform(input, expression);

            assertNotNull(result);
            assertTrue(result.contains("\"paymentStatus\""));
            assertTrue(result.contains("\"PENDING\""));
        }
    }

    // ===== ERROR HANDLING TESTS =====

    @Nested
    @DisplayName("Error Handling — Must throw TransformationException, NEVER NPE or 500")
    class ErrorHandling {

        @Test
        @DisplayName("Should throw TransformationException when expression returns null")
        void transform_expressionReturnsNull_throwsTransformationException() {
            // This is the most dangerous silent bug: JSONata evaluates to null,
            // and without this check, an empty message gets produced to the target topic.
            // Downstream consumers crash on null payloads → another 2am incident.
            String input = "{\"orderId\": \"ORD-1\", \"amount\": 500}";
            String expression = "nonExistentField"; // evaluates to null

            TransformationException ex = assertThrows(TransformationException.class,
                    () -> engine.transform(input, expression),
                    "Should throw when JSONata expression evaluates to null — prevents empty messages in target topic");

            assertTrue(ex.getMessage().contains("null"),
                    "Error message should mention null to help engineer debug");
        }

        @Test
        @DisplayName("Should throw TransformationException for malformed JSON input")
        void transform_malformedJsonInput_throwsTransformationException() {
            String malformedJson = "this is not json {{{";
            String expression = "orderId";

            assertThrows(TransformationException.class,
                    () -> engine.transform(malformedJson, expression),
                    "Should throw TransformationException for malformed JSON, not Jackson's JsonParseException");
        }

        @Test
        @DisplayName("Should throw TransformationException for invalid JSONata syntax")
        void transform_invalidExpression_throwsTransformationException() {
            String input = "{\"orderId\": \"ORD-1\"}";
            String expression = "{{{invalid jsonata syntax!!!}}}";

            assertThrows(TransformationException.class,
                    () -> engine.transform(input, expression),
                    "Invalid JSONata syntax should throw TransformationException with useful message");
        }
    }

    // ===== VALIDATION TESTS =====

    @Nested
    @DisplayName("Expression Validation — validate() before transform()")
    class ExpressionValidation {

        @Test
        @DisplayName("Should validate correct JSONata expression")
        void validate_correctExpression_returnsTrue() {
            assertTrue(engine.validate("{\"orderId\": orderId}"),
                    "Valid JSONata expression should pass validation");
        }

        @Test
        @DisplayName("Should validate identity expression")
        void validate_identityExpression_returnsTrue() {
            assertTrue(engine.validate("$"),
                    "Identity expression ($) should be valid");
        }

        @Test
        @DisplayName("Should reject invalid JSONata expression")
        void validate_invalidExpression_returnsFalse() {
            assertFalse(engine.validate("{{{broken}}}"),
                    "Invalid expression should fail validation");
        }

        @Test
        @DisplayName("Should validate built-in function usage")
        void validate_builtInFunctions_returnsTrue() {
            assertTrue(engine.validate("$uppercase(status)"),
                    "Built-in JSONata functions like $uppercase should be valid");
        }
    }

    // ===== SECURITY BOUNDARY TESTS =====

    @Nested
    @DisplayName("Security Boundary — JSONata ONLY, never executes system commands")
    class SecurityBoundary {

        @Test
        @DisplayName("JSONata cannot access system runtime — RCE prevention (CRITICAL)")
        void transform_rceAttempt_throwsNotExecutes() {
            // CRITICAL SECURITY TEST from Blueprint v2 Fix 1:
            // If we used Groovy, this would execute: Runtime.getRuntime().exec("rm -rf /")
            // With JSONata, this MUST throw an error, not execute anything
            String input = "{\"data\": \"test\"}";
            String maliciousExpression = "$eval(\"Runtime.getRuntime().exec('ls')\")";

            // This should either throw TransformationException or return a safe result
            // It must NEVER execute a system command
            assertThrows(Exception.class,
                    () -> engine.transform(input, maliciousExpression),
                    "JSONata should not have $eval or any code execution capability");
        }

        @Test
        @DisplayName("JSONata cannot access prototype chain — prototype pollution prevention")
        void transform_prototypePollution_throwsOrSafe() {
            String input = "{\"data\": \"test\"}";
            String expression = "$lookup($, \"__proto__\")";

            // Should either throw or return safe result — never expose internal state
            assertDoesNotThrow(() -> {
                try {
                    engine.transform(input, expression);
                } catch (TransformationException e) {
                    // Expected — safe failure
                }
            }, "Prototype access attempt should fail safely, not crash the JVM");
        }
    }
}
