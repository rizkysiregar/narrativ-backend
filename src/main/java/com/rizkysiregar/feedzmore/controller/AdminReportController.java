package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.AdminReportDTO;
import com.rizkysiregar.feedzmore.model.request.AdminReportSearchRequest;
import com.rizkysiregar.feedzmore.model.request.DismissReportRequest;
import com.rizkysiregar.feedzmore.model.request.ResolveReportRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportService reportService;

    @Operation(summary = "Admin report queue",
            description = "status: OPEN, RESOLVED or DISMISSED. targetType: POST, COMMENT or USER. Newest first by default, pass sort=createdAt,asc for the oldest first. createdFrom/createdTo are inclusive dates (yyyy-MM-dd) in Asia/Jakarta time.")
    @GetMapping
    public PaginatedResponse<AdminReportDTO> searchReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String reporterId,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @Parameter(hidden = true)
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return reportService.search(new AdminReportSearchRequest(
                status, targetType, reason, reporterId, targetId, createdFrom, createdTo), pageable);
    }

    @Operation(summary = "Admin report detail with the target as it is now and how many reports point at it")
    @GetMapping("{reportId}")
    public AdminReportDTO getReport(@PathVariable @NotBlank String reportId) {
        return reportService.getReport(reportId);
    }

    @Operation(summary = "Admin resolve a report",
            description = "action: NONE, TAKEDOWN (post or comment), BAN (bans the owner of the target) or HARD_DELETE (post only). Every action except NONE needs a note of 5-500 characters, which is also the reason of the action. The other open reports of the same target are closed along with it. If the action is refused the report stays open.")
    @PostMapping("{reportId}/resolve")
    public AdminReportDTO resolveReport(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable @NotBlank String reportId,
            @Valid @RequestBody ResolveReportRequest request
    ) {
        return reportService.resolve(reportId, request, admin.getUserId());
    }

    @Operation(summary = "Admin dismiss a report without any action")
    @PostMapping("{reportId}/dismiss")
    public AdminReportDTO dismissReport(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable @NotBlank String reportId,
            @Valid @RequestBody(required = false) DismissReportRequest request
    ) {
        return reportService.dismiss(reportId, request, admin.getUserId());
    }
}
