package com.rizkysiregar.feedzmore.service.firebase;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.entity.FcmBroadcastEntity;
import com.rizkysiregar.feedzmore.repository.FcmBroadcastMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
@Slf4j
public class FirebaseSchedulerService {
    private final FirebaseNotificationService notificationService;
    private final FcmBroadcastMessageRepository broadcastMessageRepository;

    public FirebaseSchedulerService(FirebaseNotificationService notificationService,
                                    FcmBroadcastMessageRepository broadcastMessageRepository) {
        this.notificationService = notificationService;
        this.broadcastMessageRepository = broadcastMessageRepository;
    }

    //@Scheduled(fixedRate = 8 * 60 * 60 * 1000) // Every 8 hours or (28,800,000 ms)
    @Scheduled(fixedRate = 30 * 1000) // Runs every 30 seconds or (30,000 ms)
    public void sendSchedulerNotification() {
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

        } else {
            log.error("## ERROR Broadcast with message : {} NOT FOUND !", messageName);
            throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
        }
    }

}
