package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.Constants;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminBroadcastDTO;
import com.rizkysiregar.feedzmore.model.entity.NotificationBroadcastEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.BroadcastStatus;
import com.rizkysiregar.feedzmore.model.request.BroadcastNotificationRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.NotificationBroadcastRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.firebase.FirebaseNotificationService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Push notifications an admin sends to an FCM topic, with a history of every attempt. The call to Firebase is
 * made outside of any database transaction: the history row and the audit entry are written afterwards, in one
 * transaction of their own, and also when Firebase refused the message.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminBroadcastService {

    static final Duration SEND_TIMEOUT = Duration.ofSeconds(15);
    private static final int ERROR_MAX = 500;

    private final FirebaseNotificationService firebaseNotificationService;
    private final NotificationBroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    /** @return the Firebase answer, which the old /notifications/broadcast endpoint returns as it always did */
    public NotificationBroadcastEntity broadcast(BroadcastNotificationRequest request, UUID adminId) {
        String response;
        try {
            response = firebaseNotificationService
                    .sendNotificationToTopic(request.getTopic(), request.getTitle(), request.getBody())
                    .block(SEND_TIMEOUT);
        } catch (Exception e) {
            log.error("BROADCAST.FAILED topic={} reason={}", request.getTopic(), e.getMessage());
            record(adminId, request, BroadcastStatus.FAILED, null, truncate(e.getMessage()));
            throw new BusinessException(GlobalMessage.BROADCAST_FAILED, HttpStatus.BAD_GATEWAY);
        }
        return record(adminId, request, BroadcastStatus.SENT, response == null ? "" : response, null);
    }

    @Transactional(readOnly = true)
    public AdminBroadcastDTO toDto(NotificationBroadcastEntity entity) {
        return AdminBroadcastDTO.from(entity, userRepository.findById(entity.getAdminId()).map(UserEntity::getUsername).orElse(null));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<AdminBroadcastDTO> search(BroadcastStatus status, String topic, UUID adminId,
                                                       LocalDate createdFrom, LocalDate createdTo, Pageable pageable) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        Instant from = createdFrom == null ? null : createdFrom.atStartOfDay(Constants.ADMIN_ZONE).toInstant();
        Instant before = createdTo == null ? null : createdTo.plusDays(1).atStartOfDay(Constants.ADMIN_ZONE).toInstant();

        Page<NotificationBroadcastEntity> page = broadcastRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (topic != null && !topic.isBlank()) {
                predicates.add(cb.equal(root.get("topic"), topic.trim()));
            }
            if (adminId != null) {
                predicates.add(cb.equal(root.get("adminId"), adminId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (before != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), before));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        Set<UUID> adminIds = page.getContent().stream().map(NotificationBroadcastEntity::getAdminId).collect(Collectors.toSet());
        Map<UUID, String> usernames = new HashMap<>();
        userRepository.findAllById(adminIds).forEach(user -> usernames.put(user.getId(), user.getUsername()));

        return new PaginatedResponse<>(
                page.getContent().stream().map(entity -> AdminBroadcastDTO.from(entity, usernames.get(entity.getAdminId()))).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    private NotificationBroadcastEntity record(UUID adminId, BroadcastNotificationRequest request,
                                               BroadcastStatus status, String fcmResponse, String error) {
        return transactionTemplate.execute(transaction -> {
            NotificationBroadcastEntity saved = broadcastRepository.save(NotificationBroadcastEntity.builder()
                    .adminId(adminId)
                    .topic(request.getTopic())
                    .title(request.getTitle())
                    .body(request.getBody())
                    .status(status)
                    .fcmResponse(fcmResponse)
                    .error(error)
                    .build());
            auditLogService.record(AdminAuditAction.BROADCAST_SEND, AuditTargetType.BROADCAST, saved.getId().toString(),
                    null, AuditLogService.metadata("topic", request.getTopic(), "title", request.getTitle(), "status", status.name()));
            return saved;
        });
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MAX ? message.substring(0, ERROR_MAX) : message;
    }
}
