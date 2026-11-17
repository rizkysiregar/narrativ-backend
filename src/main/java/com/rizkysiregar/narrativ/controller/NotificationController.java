package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.request.BroadcastNotificationRequest;
import com.rizkysiregar.narrativ.service.AdminBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 07/11/25
 * Description: Programmer
 */

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final AdminBroadcastService broadcastService;

    /**
     * @deprecated moved to POST /admin/notifications/broadcast. Kept for one release as an alias that now goes
     * through the same validation, history and audit log, and still answers with the Firebase response as before.
     */
    @Deprecated
    @Operation(summary = "DEPRECATED, use POST /admin/notifications/broadcast. Broadcast notification via firebase by an admin", description = "only admin role that can use this api")
    @PostMapping("broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminBroadcastTopic(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal admin,
            @Valid @RequestBody BroadcastNotificationRequest request
    ) {
        return broadcastService.broadcast(request, admin.getUserId()).getFcmResponse();
    }
}
