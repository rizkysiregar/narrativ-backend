package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminNotificationTemplateDTO;
import com.rizkysiregar.feedzmore.model.entity.NotificationTemplateEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.TemplateCategory;
import com.rizkysiregar.feedzmore.model.enums.TemplateMode;
import com.rizkysiregar.feedzmore.model.request.CreateNotificationTemplateRequest;
import com.rizkysiregar.feedzmore.model.request.UpdateNotificationTemplateRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.NotificationTemplateRepository;
import com.rizkysiregar.feedzmore.util.LikePatterns;
import com.rizkysiregar.feedzmore.util.Operation;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * App management: the notification templates. The scheduler picks a random active CASUAL + STATIC template for
 * its push to everybody, so what is edited here is what users receive. Configuration, not user content: an admin
 * may delete a template for good, every change is audited.
 */
@Service
@RequiredArgsConstructor
public class AdminNotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PaginatedResponse<AdminNotificationTemplateDTO> search(TemplateCategory category, TemplateMode mode,
                                                                  Boolean active, String keyword, Pageable pageable) {
        String pattern = keyword == null || keyword.isBlank() ? null : LikePatterns.contains(keyword);
        Page<NotificationTemplateEntity> page = templateRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category.name()));
            }
            if (mode != null) {
                predicates.add(cb.equal(root.get("mode"), mode.name()));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("isActive"), active));
            }
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("title")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("body")), pattern, LikePatterns.ESCAPE)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        return new PaginatedResponse<>(
                page.getContent().stream().map(AdminNotificationTemplateDTO::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public AdminNotificationTemplateDTO get(UUID id) {
        return AdminNotificationTemplateDTO.from(find(id));
    }

    @Transactional
    public AdminNotificationTemplateDTO create(CreateNotificationTemplateRequest request) {
        if (templateRepository.existsByCode(request.code())) {
            throw new BusinessException(GlobalMessage.TEMPLATE_CODE_EXISTS, HttpStatus.CONFLICT);
        }

        NotificationTemplateEntity entity = new NotificationTemplateEntity();
        entity.setCode(request.code());
        entity.setTitle(request.title().trim());
        entity.setBody(request.body().trim());
        entity.setMode(request.mode().name());
        entity.setCategory(request.category().name());
        entity.setTopic(blankToNull(request.topic()));
        entity.setActive(request.active() == null || request.active());
        NotificationTemplateEntity saved;
        try {
            saved = templateRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(GlobalMessage.TEMPLATE_CODE_EXISTS, HttpStatus.CONFLICT);
        }

        auditLogService.record(AdminAuditAction.TEMPLATE_CREATE, AuditTargetType.NOTIFICATION_TEMPLATE,
                saved.getId().toString(), null, snapshot(saved));
        return AdminNotificationTemplateDTO.from(saved);
    }

    @Transactional
    public AdminNotificationTemplateDTO update(UUID id, UpdateNotificationTemplateRequest request) {
        if (request.title() == null && request.body() == null && request.mode() == null
                && request.category() == null && request.topic() == null && request.active() == null) {
            throw new BusinessException(GlobalMessage.NOTHING_TO_UPDATE, HttpStatus.BAD_REQUEST);
        }
        NotificationTemplateEntity entity = find(id);

        List<String> fields = new ArrayList<>();
        Map<String, Object> metadata = AuditLogService.metadata("code", entity.getCode());
        String title = request.title() == null ? null : request.title().trim();
        String body = request.body() == null ? null : request.body().trim();
        if (title != null && !title.equals(entity.getTitle())) {
            fields.add("title");
            entity.setTitle(title);
        }
        if (body != null && !body.equals(entity.getBody())) {
            fields.add("body");
            entity.setBody(body);
        }
        if (request.mode() != null && !request.mode().name().equals(entity.getMode())) {
            fields.add("mode");
            entity.setMode(request.mode().name());
        }
        if (request.category() != null && !request.category().name().equals(entity.getCategory())) {
            fields.add("category");
            entity.setCategory(request.category().name());
        }
        if (request.topic() != null && !Objects.equals(request.topic(), entity.getTopic())) {
            fields.add("topic");
            entity.setTopic(request.topic());
        }
        if (request.active() != null && request.active() != entity.isActive()) {
            fields.add("active");
            metadata.put("previousActive", entity.isActive());
            metadata.put("newActive", request.active());
            entity.setActive(request.active());
        }
        if (fields.isEmpty()) {
            return AdminNotificationTemplateDTO.from(entity);
        }

        NotificationTemplateEntity saved = templateRepository.save(entity);
        metadata.put("fields", fields);
        auditLogService.record(AdminAuditAction.TEMPLATE_UPDATE, AuditTargetType.NOTIFICATION_TEMPLATE,
                saved.getId().toString(), null, metadata);
        return AdminNotificationTemplateDTO.from(saved);
    }

    @Transactional
    public Operation delete(UUID id) {
        NotificationTemplateEntity entity = find(id);
        templateRepository.delete(entity);

        auditLogService.record(AdminAuditAction.TEMPLATE_DELETE, AuditTargetType.NOTIFICATION_TEMPLATE,
                entity.getId().toString(), null, snapshot(entity));
        return Operation.DELETED;
    }

    // what a template was, so a deleted one can be put back by hand
    private Map<String, Object> snapshot(NotificationTemplateEntity entity) {
        return AuditLogService.metadata(
                "code", entity.getCode(), "title", entity.getTitle(), "body", entity.getBody(),
                "mode", entity.getMode(), "category", entity.getCategory(), "topic", entity.getTopic(),
                "active", entity.isActive());
    }

    private NotificationTemplateEntity find(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
