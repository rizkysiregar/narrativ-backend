package com.rizkysiregar.narrativ.model.dto;

import com.rizkysiregar.narrativ.model.entity.NotificationBroadcastEntity;
import com.rizkysiregar.narrativ.model.enums.BroadcastStatus;

import java.time.Instant;

public record AdminBroadcastDTO(
        String id,
        String adminId,
        String adminUsername,
        String topic,
        String title,
        String body,
        BroadcastStatus status,
        String fcmResponse,
        String error,
        Instant createdAt
) {
    public static AdminBroadcastDTO from(NotificationBroadcastEntity entity, String adminUsername) {
        return new AdminBroadcastDTO(entity.getId().toString(), entity.getAdminId().toString(), adminUsername,
                entity.getTopic(), entity.getTitle(), entity.getBody(), entity.getStatus(), entity.getFcmResponse(),
                entity.getError(), entity.getCreatedAt());
    }
}
