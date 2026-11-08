package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.AdminNotificationTemplateDTO;
import com.rizkysiregar.feedzmore.model.enums.TemplateCategory;
import com.rizkysiregar.feedzmore.model.enums.TemplateMode;
import com.rizkysiregar.feedzmore.model.request.CreateNotificationTemplateRequest;
import com.rizkysiregar.feedzmore.model.request.UpdateNotificationTemplateRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.AdminNotificationTemplateService;
import com.rizkysiregar.feedzmore.util.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/notification-templates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationTemplateController {

    private final AdminNotificationTemplateService templateService;

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin list notification templates",
            description = "category: CASUAL, EVENT, ADMIN or OTP. mode: STATIC, TEMPLATE or DIRECT. keyword matches the code, title or body. Newest first by default.")
    @GetMapping
    public PaginatedResponse<AdminNotificationTemplateDTO> search(
            @RequestParam(required = false) TemplateCategory category,
            @RequestParam(required = false) TemplateMode mode,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @Parameter(hidden = true) @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return templateService.search(category, mode, active, keyword, pageable);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin get a notification template")
    @GetMapping("{id}")
    public AdminNotificationTemplateDTO get(@PathVariable UUID id) {
        return templateService.get(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin create a notification template",
            description = "The code cannot be changed afterwards. The scheduler sends a random active CASUAL + STATIC template to everybody every 8 hours.")
    @PostMapping
    public AdminNotificationTemplateDTO create(@Valid @RequestBody CreateNotificationTemplateRequest request) {
        return templateService.create(request);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin change a notification template (also to switch it on or off with active)")
    @PatchMapping("{id}")
    public AdminNotificationTemplateDTO update(@PathVariable UUID id, @Valid @RequestBody UpdateNotificationTemplateRequest request) {
        return templateService.update(id, request);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin delete a notification template for good", description = "IRREVERSIBLE. What was deleted is kept in the audit log. Switch it off with active=false to keep it.")
    @DeleteMapping("{id}")
    public Operation delete(@PathVariable UUID id) {
        return templateService.delete(id);
    }
}
