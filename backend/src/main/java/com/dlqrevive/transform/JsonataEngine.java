package com.dlqrevive.transform;

import com.api.jsonata4java.expressions.Expressions;
import com.api.jsonata4java.expressions.EvaluateException;
import com.api.jsonata4java.expressions.ParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * JsonataEngine — Safe, declarative JSON-to-JSON transformation.
 *
 * SECURITY RULE: JSONata ONLY. NEVER Groovy. NEVER JavaScript eval().
 * JSONata is a purely declarative query/transform language with:
 * - Zero system access
 * - Zero file access
 * - Zero network access
 * - Safe to execute in any sandbox
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
            JsonNode inputData = objectMapper.readTree(jsonInput);

            Expressions expr = Expressions.parse(expression);
            JsonNode result = expr.evaluate(inputData);

            if (result == null || result.isNull()) {
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
            Expressions.parse(expression);
            return true;
        } catch (Exception e) {
            log.debug("Invalid JSONata expression: {}", e.getMessage());
            return false;
        }
    }
}
