package com.rizkysiregar.feedzmore.config;

import com.rizkysiregar.feedzmore.service.firebase.FCMService;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private final FCMService fcmService;

    public NotificationScheduler(FCMService fcmService) {
        this.fcmService = fcmService;
    }




}
