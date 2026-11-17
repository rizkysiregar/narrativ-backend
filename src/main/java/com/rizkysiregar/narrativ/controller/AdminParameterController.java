package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.dto.AdminParameterDTO;
import com.rizkysiregar.narrativ.model.request.CreateParameterRequest;
import com.rizkysiregar.narrativ.model.request.UpdateParameterRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.service.AdminParameterService;
import com.rizkysiregar.narrativ.util.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/parameters")
@PreAuthorize("hasRole('ADMIN')")
public class AdminParameterController {

    private final AdminParameterService parameterService;

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin list app parameters", description = "keyword matches the key or the description. Sorted by key by default.")
    @GetMapping
    public PaginatedResponse<AdminParameterDTO> search(
            @RequestParam(required = false) String keyword,
            @Parameter(hidden = true) @PageableDefault(sort = "paramKey", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return parameterService.search(keyword, pageable);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin get an app parameter")
    @GetMapping("{id}")
    public AdminParameterDTO get(@PathVariable Long id) {
        return parameterService.get(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin create an app parameter", description = "The key cannot be changed afterwards.")
    @PostMapping
    public AdminParameterDTO create(@Valid @RequestBody CreateParameterRequest request) {
        return parameterService.create(request);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin change the value and/or description of an app parameter")
    @PatchMapping("{id}")
    public AdminParameterDTO update(@PathVariable Long id, @Valid @RequestBody UpdateParameterRequest request) {
        return parameterService.update(id, request);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Admin delete an app parameter for good", description = "IRREVERSIBLE. What was deleted is kept in the audit log.")
    @DeleteMapping("{id}")
    public Operation delete(@PathVariable Long id) {
        return parameterService.delete(id);
    }
}
