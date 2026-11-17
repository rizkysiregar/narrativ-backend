package com.rizkysiregar.narrativ.model.dto;

import com.rizkysiregar.narrativ.model.entity.ParameterEntity;

import java.time.LocalDateTime;

public record AdminParameterDTO(
        Long id,
        String paramKey,
        String paramValue,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminParameterDTO from(ParameterEntity entity) {
        return new AdminParameterDTO(entity.getId(), entity.getParamKey(), entity.getParamValue(),
                entity.getDescription(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
