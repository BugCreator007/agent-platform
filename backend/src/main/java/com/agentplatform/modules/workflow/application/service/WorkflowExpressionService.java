package com.agentplatform.modules.workflow.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WorkflowExpressionService {
    private final ObjectMapper mapper;
    private final ExpressionParser parser = new SpelExpressionParser();

    public WorkflowExpressionService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean evaluate(String expression, String inputJson, Map<String, String> outputs) {
        if (expression == null || expression.isBlank()) return true;
        Map<String, Object> variables = variables(inputJson, outputs);
        SimpleEvaluationContext context = SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor())
                .withInstanceMethods()
                .build();
        try {
            Expression parsed = parser.parseExpression(expression);
            Object value = parsed.getValue(context, variables);
            if (value instanceof Boolean b) return b;
            return value != null && Boolean.parseBoolean(String.valueOf(value));
        } catch (Exception e) {
            // Missing variables and malformed expressions are treated as a false branch,
            // instead of turning the whole workflow into a failed run.
            return false;
        }
    }

    public Map<String, Object> variables(String inputJson, Map<String, String> outputs) {
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> input = parse(inputJson);
        flatten(variables, input, null);
        variables.put("input", input);
        variables.put("text", inputJson == null ? "" : inputJson);
        Map<String, Object> nodeOutputs = new LinkedHashMap<>();
        if (outputs != null) {
            for (Map.Entry<String, String> entry : outputs.entrySet()) {
                Map<String, Object> parsed = parse(entry.getValue());
                nodeOutputs.put(entry.getKey(), parsed);
                flatten(variables, parsed, entry.getKey());
            }
        }
        variables.put("nodes", nodeOutputs);
        variables.put("outputs", nodeOutputs);
        return variables;
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Object value = mapper.readValue(json, Object.class);
            return value instanceof Map<?, ?> map ? toStringMap(map) : Map.of("value", value);
        } catch (Exception e) {
            return Map.of("value", json);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private void flatten(Map<String, Object> target, Map<String, Object> source, String prefix) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix == null ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = normalize(entry.getValue());
            put(target, key, value);
            put(target, entry.getKey(), value);
            if (value instanceof Map<?, ?> nested) {
                flatten(target, toStringMap(nested), key);
            }
        }
    }

    private Object normalize(Object value) {
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
                try { return Double.parseDouble(trimmed); } catch (NumberFormatException ignored) { }
            }
            if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                return Boolean.parseBoolean(trimmed);
            }
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    Object nested = mapper.readValue(trimmed, Object.class);
                    return nested instanceof Map<?, ?> map ? toStringMap(map) : nested;
                } catch (Exception ignored) { }
            }
        }
        return value;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        target.putIfAbsent(key, value);
        target.putIfAbsent(toSnakeCase(key), value);
    }

    private String toSnakeCase(String key) {
        return key.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
