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
        Expression parsed = parser.parseExpression(expression);
        Object value = parsed.getValue(context, variables);
        if (value instanceof Boolean b) return b;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    public Map<String, Object> variables(String inputJson, Map<String, String> outputs) {
        Map<String, Object> variables = new LinkedHashMap<>();
        merge(variables, parse(inputJson));
        Map<String, Object> nodeOutputs = new LinkedHashMap<>();
        if (outputs != null) {
            for (Map.Entry<String, String> entry : outputs.entrySet()) {
                Object parsed = parse(entry.getValue());
                nodeOutputs.put(entry.getKey(), parsed);
                if (parsed instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> item : map.entrySet()) {
                        variables.putIfAbsent(String.valueOf(item.getKey()), item.getValue());
                    }
                }
            }
        }
        variables.put("nodes", nodeOutputs);
        return variables;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Object value = mapper.readValue(json, Object.class);
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of("value", value);
        } catch (Exception e) {
            return Map.of("value", json);
        }
    }

    @SuppressWarnings("unchecked")
    private void merge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text && text.matches("-?\\d+(\\.\\d+)?")) {
                try { value = Double.parseDouble(text); } catch (NumberFormatException ignored) { }
            } else if ("true".equalsIgnoreCase(String.valueOf(value)) || "false".equalsIgnoreCase(String.valueOf(value))) {
                value = Boolean.parseBoolean(String.valueOf(value));
            }
            if (value instanceof Map<?, ?> nested) {
                target.put(entry.getKey(), nested);
                for (Map.Entry<?, ?> child : nested.entrySet()) {
                    target.putIfAbsent(String.valueOf(child.getKey()), child.getValue());
                }
            } else {
                target.put(entry.getKey(), value);
            }
        }
    }
}
