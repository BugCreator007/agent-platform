package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.domain.model.WorkflowNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class WorkflowNodeListConverter implements AttributeConverter<List<WorkflowNode>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<WorkflowNode> attribute) {
        try { return attribute == null ? "[]" : MAPPER.writeValueAsString(attribute); }
        catch (Exception e) { throw new IllegalArgumentException("nodes serialize failed", e); }
    }

    @Override
    public List<WorkflowNode> convertToEntityAttribute(String dbData) {
        try { return dbData == null || dbData.isBlank() ? List.of() : MAPPER.readValue(dbData, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }
}
;