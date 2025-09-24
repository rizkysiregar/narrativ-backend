package com.rizkysiregar.feedzmore.controller;

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
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {
    private final FirebaseNotificationService notificationService;

    @PostMapping("/admin/broadcast/topic")
    public Mono<String> adminBroadcastTopic(
            @RequestParam String title,
            @RequestParam String topic,
            @RequestParam String body
    ) {
        return notificationService.sendNotificationToTopic(topic, title, body);
    }
}
