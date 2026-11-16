package com.rizkysiregar.narrativ.model.dto;

import com.rizkysiregar.narrativ.model.entity.NotificationTemplateEntity;

import java.time.Instant;

public record AdminNotificationTemplateDTO(
        String id,
        String code,
        String title,
        String body,
        String mode,
        String category,
        String topic,
        boolean active,
        Instant createdAt
) {
    public static AdminNotificationTemplateDTO from(NotificationTemplateEntity entity) {
        return new AdminNotificationTemplateDTO(entity.getId().toString(), entity.getCode(), entity.getTitle(),
                entity.getBody(), entity.getMode(), entity.getCategory(), entity.getTopic(), entity.isActive(),
                entity.getCreatedAt());
    }
}
