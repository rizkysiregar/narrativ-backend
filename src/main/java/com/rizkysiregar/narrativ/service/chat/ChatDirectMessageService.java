package com.rizkysiregar.narrativ.service.chat;

import com.rizkysiregar.narrativ.config.MarpokatProperties;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.response.DirectMessageResponse;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Opens the direct message between the logged-in user and another user (marpokat PLAN NV-05). Who may message whom is
 * decided here, not in Marpokat: the tenant is configured so that clients cannot create DMs themselves.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatDirectMessageService {

    private final MarpokatProperties properties;
    private final MarpokatClient marpokat;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    public DirectMessageResponse open(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        if (!properties.enabled()) {
            log.warn("CHAT.DM.DISABLED reason=app.marpokat.* not configured");
            throw new BusinessException(GlobalMessage.CHAT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        UserEntity me = userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        UserEntity target = userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        // a user on either side of a block does not exist for the other, here as everywhere else in the app
        if (userBlockRepository.existsBetween(currentUserId, targetUserId)) {
            throw new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        try {
            // both users must be known to Marpokat first; this also keeps their chat profiles current
            marpokat.upsertUser(me.getId().toString(), ChatTokenService.profileOf(me));
            marpokat.upsertUser(target.getId().toString(), ChatTokenService.profileOf(target));
            String channelId = marpokat.getOrCreateDirectMessage(me.getId().toString(), target.getId().toString());
            return new DirectMessageResponse(channelId);
        } catch (MarpokatClient.MarpokatException e) {
            log.error("CHAT.DM.FAILED userId={} targetId={} reason={}", currentUserId, targetUserId, e.getMessage());
            throw new BusinessException(GlobalMessage.CHAT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
