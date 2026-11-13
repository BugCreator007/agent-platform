package com.agentplatform.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {
    private static final String SEP = "\u001F";

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return attribute == null || attribute.isEmpty() ? "" : String.join(SEP, attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        return Arrays.stream(dbData.split(SEP, -1)).filter(s -> !s.isBlank()).toList();
    }
}
