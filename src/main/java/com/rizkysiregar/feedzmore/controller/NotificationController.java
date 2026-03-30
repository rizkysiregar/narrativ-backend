package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.request.BroadcastNotificationRequest;
import com.rizkysiregar.feedzmore.service.firebase.FirebaseNotificationService;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("broadcast")
    public Mono<String> adminBroadcastTopic(
            @RequestBody BroadcastNotificationRequest request
            ) {
        return notificationService.sendNotificationToTopic(request.getTopic(), request.getTitle(), request.getBody());
    }
}
