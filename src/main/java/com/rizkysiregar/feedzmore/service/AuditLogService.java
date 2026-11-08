package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.Constants;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.AdminAuditLogDTO;
import com.rizkysiregar.feedzmore.model.entity.AdminAuditLogEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.request.AuditLogSearchRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.AdminAuditLogRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.ClientIpResolver;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final AdminAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ClientIpResolver clientIpResolver;

    /**
     * Records an admin action as the currently authenticated admin.
     * <p>
     * Must run inside the transaction of the action it describes: if the audit row cannot be written
     * the action is rolled back, so no admin write is ever left without a trace.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AdminAuditAction action, AuditTargetType targetType, String targetId,
                       String reason, Map<String, Object> metadata) {
        auditLogRepository.save(AdminAuditLogEntity.builder()
                .adminId(currentAdminId())
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadata(metadata)
                .ip(currentClientIp())
                .build());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<AdminAuditLogDTO> search(AuditLogSearchRequest request, Pageable pageable) {
        UUID adminId = request.adminId() == null || request.adminId().isBlank() ? null : parseUuid(request.adminId());
        AdminAuditAction action = parseEnum(AdminAuditAction.class, request.action());
        AuditTargetType targetType = parseEnum(AuditTargetType.class, request.targetType());

        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        Instant fromInstant = request.from() == null ? null
                : request.from().atStartOfDay(Constants.ADMIN_ZONE).toInstant();
        Instant toExclusive = request.to() == null ? null
                : request.to().plusDays(1).atStartOfDay(Constants.ADMIN_ZONE).toInstant();

        Page<AdminAuditLogEntity> page = auditLogRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (adminId != null) {
                predicates.add(cb.equal(root.get("adminId"), adminId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (targetType != null) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (request.targetId() != null && !request.targetId().isBlank()) {
                predicates.add(cb.equal(root.get("targetId"), request.targetId()));
            }
            if (fromInstant != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        Set<UUID> adminIds = page.getContent().stream()
                .map(AdminAuditLogEntity::getAdminId)
                .collect(Collectors.toSet());
        Map<UUID, String> usernames = userRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername));

        List<AdminAuditLogDTO> content = page.getContent().stream()
                .map(entity -> AdminAuditLogDTO.from(entity, usernames.get(entity.getAdminId())))
                .toList();

        return new PaginatedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    /** Convenience for building audit metadata; entries with a null value are skipped. */
    public static Map<String, Object> metadata(Object... keyValues) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return result;
    }

    private UUID currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        // admin actions are only reachable through authenticated /admin endpoints; fail loudly otherwise
        throw new IllegalStateException("Audit log requires an authenticated admin");
    }

    private String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return clientIpResolver.resolve(attributes.getRequest());
        }
        return null;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }
}
