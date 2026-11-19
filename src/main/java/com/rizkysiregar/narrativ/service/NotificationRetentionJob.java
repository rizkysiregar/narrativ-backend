package com.rizkysiregar.narrativ.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/** Keeps the inbox to its last {@code app.notifications.retention} (90 days by default). */
@Component
@Slf4j
public class NotificationRetentionJob {

    private final NotificationInboxService inboxService;
    private final Clock clock;
    private final Duration retention;

    public NotificationRetentionJob(NotificationInboxService inboxService, Clock clock,
                                    @Value("${app.notifications.retention:P90D}") Duration retention) {
        this.inboxService = inboxService;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(initialDelayString = "${app.notifications.cleanup.initial-delay:PT15M}",
            fixedDelayString = "${app.notifications.cleanup.interval:PT24H}")
    public void run() {
        try {
            int deleted = inboxService.deleteOlderThan(clock.instant().minus(retention));
            log.info("INBOX.RETENTION.DELETED count={} retention={}", deleted, retention);
        } catch (Exception e) {
            log.error("INBOX.RETENTION.FAILED reason={}", e.getMessage(), e);
        }
    }
}
