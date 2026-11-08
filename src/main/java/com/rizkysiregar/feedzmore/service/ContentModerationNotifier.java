package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.Constants;
import com.rizkysiregar.feedzmore.service.firebase.FirebaseNotificationService;
import com.rizkysiregar.feedzmore.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tells the owner his content was taken down. Push only: there is no in-app inbox yet
 * (UserNotificationEntity has no repository or endpoint). Sent after the takedown is committed.
 */
@Component
@RequiredArgsConstructor
public class ContentModerationNotifier {

    private final FirebaseNotificationService notificationService;

    public void postTakenDown(UUID ownerId, String reason) {
        AfterCommit.run("post takedown push to " + ownerId, () -> notificationService.sendNotificationWithUserId(
                ownerId,
                Constants.NOTIFICATION_POST_TAKEDOWN_TITLE,
                String.format(Constants.NOTIFICATION_POST_TAKEDOWN_BODY, reason)));
    }

    public void commentTakenDown(UUID ownerId, String reason) {
        AfterCommit.run("comment takedown push to " + ownerId, () -> notificationService.sendNotificationWithUserId(
                ownerId,
                Constants.NOTIFICATION_COMMENT_TAKEDOWN_TITLE,
                String.format(Constants.NOTIFICATION_COMMENT_TAKEDOWN_BODY, reason)));
    }
}
