package com.agv.dispatch.common.entity;

import com.agv.dispatch.common.enums.TaskType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter(autoApply = false)
public class TaskTypeListConverter implements AttributeConverter<List<TaskType>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<TaskType> taskTypes) {
        if (taskTypes == null || taskTypes.isEmpty()) {
            return "";
        }
        return taskTypes.stream()
                .map(type -> String.valueOf(type.getCode()))
                .collect(Collectors.joining(SEPARATOR));
    }

    @Override
    public List<TaskType> convertToEntityAttribute(String s) {
        if (s == null || s.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(s.split(SEPARATOR))
                .filter(code -> !code.trim().isEmpty())
                .map(code -> TaskType.of(Integer.parseInt(code.trim())))
                .collect(Collectors.toList());
    }
}
