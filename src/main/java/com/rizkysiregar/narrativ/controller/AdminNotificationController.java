package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.dto.AdminBroadcastDTO;
import com.rizkysiregar.narrativ.model.enums.BroadcastStatus;
import com.rizkysiregar.narrativ.model.request.BroadcastNotificationRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.service.AdminBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private final AdminBroadcastService broadcastService;

    @Operation(summary = "Admin send a push notification to a topic",
            description = "REAL push: it reaches every device subscribed to the topic ('global' is everybody) and cannot be recalled. Every attempt is kept in the history, also when Firebase refused it (then 502).")
    @PostMapping("broadcast")
    public AdminBroadcastDTO broadcast(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal admin,
            @Valid @RequestBody BroadcastNotificationRequest request
    ) {
        return broadcastService.toDto(broadcastService.broadcast(request, admin.getUserId()));
    }

    @Operation(summary = "Admin history of the push notifications that were sent",
            description = "status: SENT or FAILED. createdFrom/createdTo are inclusive dates (yyyy-MM-dd) in Asia/Jakarta time. Newest first.")
    @GetMapping("broadcasts")
    public PaginatedResponse<AdminBroadcastDTO> broadcasts(
            @RequestParam(required = false) BroadcastStatus status,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) UUID adminId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @Parameter(hidden = true) @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return broadcastService.search(status, topic, adminId, createdFrom, createdTo, pageable);
    }
}
