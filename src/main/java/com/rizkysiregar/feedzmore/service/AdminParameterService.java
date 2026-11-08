package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminParameterDTO;
import com.rizkysiregar.feedzmore.model.entity.ParameterEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.request.CreateParameterRequest;
import com.rizkysiregar.feedzmore.model.request.UpdateParameterRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.ParameterRepository;
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

/**
 * App management: the key/value parameters. This is configuration, not user content, so an admin may also
 * delete a parameter for good; every change is audited, with the old and the new value.
 */
@Service
@RequiredArgsConstructor
public class AdminParameterService {

    private final ParameterRepository parameterRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PaginatedResponse<AdminParameterDTO> search(String keyword, Pageable pageable) {
        String pattern = keyword == null || keyword.isBlank() ? null : LikePatterns.contains(keyword);
        Page<ParameterEntity> page = parameterRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("paramKey")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("description")), pattern, LikePatterns.ESCAPE)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        return new PaginatedResponse<>(
                page.getContent().stream().map(AdminParameterDTO::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public AdminParameterDTO get(Long id) {
        return AdminParameterDTO.from(find(id));
    }

    @Transactional
    public AdminParameterDTO create(CreateParameterRequest request) {
        if (parameterRepository.existsByParamKey(request.paramKey())) {
            throw new BusinessException(GlobalMessage.PARAMETER_KEY_EXISTS, HttpStatus.CONFLICT);
        }

        ParameterEntity entity = new ParameterEntity();
        entity.setParamKey(request.paramKey());
        entity.setParamValue(request.paramValue().trim());
        entity.setDescription(request.description().trim());
        ParameterEntity saved;
        try {
            saved = parameterRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // two creates of the same key at once: the unique constraint is the real guard
            throw new BusinessException(GlobalMessage.PARAMETER_KEY_EXISTS, HttpStatus.CONFLICT);
        }

        auditLogService.record(AdminAuditAction.PARAMETER_CREATE, AuditTargetType.PARAMETER, saved.getId().toString(),
                null, AuditLogService.metadata(
                        "paramKey", saved.getParamKey(), "paramValue", saved.getParamValue(), "description", saved.getDescription()));
        return AdminParameterDTO.from(saved);
    }

    @Transactional
    public AdminParameterDTO update(Long id, UpdateParameterRequest request) {
        if (request.paramValue() == null && request.description() == null) {
            throw new BusinessException(GlobalMessage.NOTHING_TO_UPDATE, HttpStatus.BAD_REQUEST);
        }
        ParameterEntity entity = find(id);

        List<String> fields = new ArrayList<>();
        Map<String, Object> metadata = AuditLogService.metadata("paramKey", entity.getParamKey());
        String value = request.paramValue() == null ? null : request.paramValue().trim();
        String description = request.description() == null ? null : request.description().trim();
        if (value != null && !value.equals(entity.getParamValue())) {
            fields.add("paramValue");
            metadata.put("previousValue", entity.getParamValue());
            metadata.put("newValue", value);
            entity.setParamValue(value);
        }
        if (description != null && !description.equals(entity.getDescription())) {
            fields.add("description");
            metadata.put("previousDescription", entity.getDescription());
            metadata.put("newDescription", description);
            entity.setDescription(description);
        }
        if (fields.isEmpty()) {
            // same values as now: nothing changes, so nothing is saved or audited
            return AdminParameterDTO.from(entity);
        }

        ParameterEntity saved = parameterRepository.save(entity);
        metadata.put("fields", fields);
        auditLogService.record(AdminAuditAction.PARAMETER_UPDATE, AuditTargetType.PARAMETER, saved.getId().toString(), null, metadata);
        return AdminParameterDTO.from(saved);
    }

    @Transactional
    public Operation delete(Long id) {
        ParameterEntity entity = find(id);
        parameterRepository.delete(entity);

        auditLogService.record(AdminAuditAction.PARAMETER_DELETE, AuditTargetType.PARAMETER, entity.getId().toString(),
                null, AuditLogService.metadata(
                        "paramKey", entity.getParamKey(), "paramValue", entity.getParamValue(), "description", entity.getDescription()));
        return Operation.DELETED;
    }

    private ParameterEntity find(Long id) {
        return parameterRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));
    }
}
