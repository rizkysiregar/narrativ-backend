package com.rizkysiregar.feedzmore.service.firebase;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.entity.FcmBroadcastEntity;
import com.rizkysiregar.feedzmore.model.entity.JobExecutionLog;
import com.rizkysiregar.feedzmore.repository.FcmBroadcastMessageRepository;
import com.rizkysiregar.feedzmore.repository.JobExecutionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class FirebaseSchedulerService {
    private final FirebaseNotificationService notificationService;
    private final FcmBroadcastMessageRepository broadcastMessageRepository;
    private final JobExecutionLogRepository jobExecutionLogRepository;
    private static final String FIREBASE_BROADCAST_JOB = "firebase_broadcast_job";
    private static final Duration INTERVAL_BROADCAST = Duration.ofHours(8);


    public FirebaseSchedulerService(FirebaseNotificationService notificationService,
                                    FcmBroadcastMessageRepository broadcastMessageRepository,
                                    JobExecutionLogRepository jobExecutionLogRepository
    ) {
        this.notificationService = notificationService;
        this.broadcastMessageRepository = broadcastMessageRepository;
        this.jobExecutionLogRepository = jobExecutionLogRepository;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000) // Check every hour
    //@Scheduled(fixedRate = 8 * 60 * 60 * 1000) // Every 8 hours or (28,800,000 ms)
    //@Scheduled(fixedRate = 30 * 1000) // Runs every 30 seconds or (30,000 ms) for testing
    public void sendSchedulerNotification() {
        log.info("## schedule messaging called!");
        Optional<JobExecutionLog> logOpt = jobExecutionLogRepository.findById(FIREBASE_BROADCAST_JOB);
        LocalDateTime now = LocalDateTime.now();
        if (logOpt.isEmpty()) {
            throw new BusinessException(GlobalMessage.PARAMETER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        JobExecutionLog jobExecutionLog = logOpt.get();
        if (jobExecutionLog.getLastExecutedAt().plus(INTERVAL_BROADCAST).isBefore(now)) {
            String messageName = "REUSABLE-GREETINGS";
            Optional<FcmBroadcastEntity> entity = broadcastMessageRepository.findByMessageName(messageName);
            if (entity.isPresent()) {
                Mono<String> responseMono = notificationService.sendNotificationToTopic(
                        entity.get().getTopic(),
                        entity.get().getTitle(),
                        entity.get().getBody()
                );

                responseMono.subscribe(
                        response -> log.info("Scheduler Notification Response {}", response),
                        error -> log.error("Error sending scheduler notification", error)
                );

                jobExecutionLog.setLastExecutedAt(now);
                jobExecutionLogRepository.save(jobExecutionLog);
            } else {
                log.error("## ERROR Broadcast with message : {} NOT FOUND !", messageName);
                throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
            }
        } else {
            log.info("## Not yet time to broadcast a message");
        }
    }


}
