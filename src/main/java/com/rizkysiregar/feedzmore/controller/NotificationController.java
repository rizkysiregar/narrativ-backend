package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.request.BroadcastNotificationRequest;
import com.rizkysiregar.feedzmore.service.firebase.FirebaseNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 24/09/25
 * Description: Programmer
 */

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final FirebaseNotificationService notificationService;

    @Operation(summary = "Broadcast notification via firebase by super admin or admin", description = "only admin role that can use this api")
    @PostMapping("broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<String> adminBroadcastTopic(
            @RequestBody BroadcastNotificationRequest request
            ) {
        return notificationService.sendNotificationToTopic(request.getTopic(), request.getTitle(), request.getBody());
    }
}
