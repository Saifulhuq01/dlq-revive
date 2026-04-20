package com.dlqrevive.transform;

import static com.dashjoin.jsonata.Jsonata.jsonata;

import com.dashjoin.jsonata.Jsonata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * JsonataEngine — Safe, declarative JSON-to-JSON transformation.
 *
 * SECURITY RULE: JSONata ONLY. NEVER Groovy. NEVER JavaScript eval().
 * JSONata is a purely declarative query/transform language with:
 * - Zero system access
 * - Zero file access
 * - Zero network access
 * - Safe to execute in any sandbox
 *
 * Uses the com.dashjoin:jsonata library (Java port of the JSONata reference implementation).
 */
@Service
public class JsonataEngine {

    private static final Logger log = LoggerFactory.getLogger(JsonataEngine.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Transforms a JSON string using a JSONata expression.
     *
     * @param jsonInput  The input JSON string (DLQ message value)
     * @param expression The JSONata expression to apply
     * @return The transformed JSON string
     * @throws TransformationException if the expression is invalid or transformation fails
     */
    public String transform(String jsonInput, String expression) {
        try {
            // Parse the input JSON into a Map structure (required by dashjoin jsonata)
            Map<String, Object> inputData = objectMapper.readValue(
                    jsonInput, new TypeReference<Map<String, Object>>() {});

            // Execute JSONata transformation
            // Using com.dashjoin.jsonata library — purely declarative, zero RCE surface
            Jsonata expr = jsonata(expression);
            Object result = expr.evaluate(inputData);

            if (result == null) {
                throw new TransformationException("JSONata expression returned null. " +
                        "Check that the expression matches the input structure.");
            }

            String output = objectMapper.writeValueAsString(result);
            log.debug("Transformation successful: {} chars → {} chars", jsonInput.length(), output.length());
            return output;

        } catch (TransformationException e) {
            throw e;
        } catch (Exception e) {
            log.error("JSONata transformation failed: {}", e.getMessage());
            throw new TransformationException("Transformation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates a JSONata expression without executing it against data.
     *
     * @param expression The JSONata expression to validate
     * @return true if the expression is syntactically valid
     */
    public boolean validate(String expression) {
        try {
            jsonata(expression);
            return true;
        } catch (Exception e) {
            log.debug("Invalid JSONata expression: {}", e.getMessage());
            return false;
        }
    }
}
