package com.rizkysiregar.narrativ.service.firebase;


import com.rizkysiregar.narrativ.constant.Constants;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.JobExecutionLog;
import com.rizkysiregar.narrativ.model.entity.NotificationTemplateEntity;
import com.rizkysiregar.narrativ.repository.JobExecutionLogRepository;
import com.rizkysiregar.narrativ.repository.NotificationTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class FirebaseSchedulerService {
    private final FirebaseNotificationService notificationService;
    private final JobExecutionLogRepository jobExecutionLogRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private static final String FIREBASE_BROADCAST_JOB = "firebase_broadcast_job";
    private static final Duration INTERVAL_BROADCAST = Duration.ofHours(8);
//    private static final Duration INTERVAL_BROADCAST = Duration.ofMillis(10);


    public FirebaseSchedulerService(FirebaseNotificationService notificationService,
                                    JobExecutionLogRepository jobExecutionLogRepository,
                                    NotificationTemplateRepository notificationTemplateRepository
    ) {
        this.notificationService = notificationService;
        this.jobExecutionLogRepository = jobExecutionLogRepository;
        this.notificationTemplateRepository = notificationTemplateRepository;
    }

    //@Scheduled(fixedRate = 60 * 60 * 1000) // Check every hour
    @Scheduled(fixedRate = 8 * 60 * 60 * 1000) // Every 8 hours or (28,800,000 ms)
    //@Scheduled(fixedRate = 10 * 1000) // Runs every 10 seconds or (10,000 ms) for testing
    public void sendSchedulerNotification() {
        log.debug("NOTIF.SCHEDULER.TICK");
        Optional<JobExecutionLog> logOpt = jobExecutionLogRepository.findById(FIREBASE_BROADCAST_JOB);
        LocalDateTime now = LocalDateTime.now();
        if (logOpt.isEmpty()) {
            throw new BusinessException(GlobalMessage.PARAMETER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        JobExecutionLog jobExecutionLog = logOpt.get();
        if (jobExecutionLog.getLastExecutedAt().plus(INTERVAL_BROADCAST).isBefore(now)) {

            List<NotificationTemplateEntity> casualTemplate = notificationTemplateRepository.findRandomCasual(PageRequest.of(0, 1));
            if (!casualTemplate.isEmpty()) {
                NotificationTemplateEntity notificationTemplateEntity = casualTemplate.get(0);
                Mono<String> responseMono = notificationService.sendNotificationToTopic(
                        Constants.FIREBASE_TOPIC_GLOBAL,
                        notificationTemplateEntity.getTitle(),
                        notificationTemplateEntity.getBody()
                );

                responseMono.subscribe(
                        response -> log.info("NOTIF.SCHEDULER.SENT response={}", response),
                        error -> log.error("NOTIF.SCHEDULER.FAILED", error)
                );
                jobExecutionLog.setLastExecutedAt(now);
                jobExecutionLogRepository.save(jobExecutionLog);
            } else {
                log.warn("NOTIF.SCHEDULER.SKIPPED reason=no_casual_template");
            }
        }
    }
}
