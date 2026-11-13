package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.domain.model.TraceStep;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class TraceStepListConverter implements AttributeConverter<List<TraceStep>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<TraceStep> attribute) {
        try { return attribute == null ? "[]" : MAPPER.writeValueAsString(attribute); }
        catch (Exception e) { throw new IllegalArgumentException("trace serialize failed", e); }
    }

    @Override
    public List<TraceStep> convertToEntityAttribute(String dbData) {
        try { return dbData == null || dbData.isBlank() ? List.of() : MAPPER.readValue(dbData, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }
}
;