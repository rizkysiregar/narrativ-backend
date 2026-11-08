package com.rizkysiregar.feedzmore.model.dto;

import com.rizkysiregar.feedzmore.model.entity.AdminAuditLogEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;

import java.time.Instant;
import java.util.Map;

public record AdminAuditLogDTO(
        String id,
        String adminId,
        String adminUsername,
        AdminAuditAction action,
        AuditTargetType targetType,
        String targetId,
        String reason,
        Map<String, Object> metadata,
        String ip,
        Instant createdAt
) {
    public static AdminAuditLogDTO from(AdminAuditLogEntity entity, String adminUsername) {
        return new AdminAuditLogDTO(
                entity.getId().toString(),
                entity.getAdminId().toString(),
                adminUsername,
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getReason(),
                entity.getMetadata(),
                entity.getIp(),
                entity.getCreatedAt()
        );
    }
}
