package com.rizkysiregar.narrativ.service.chat;

import com.rizkysiregar.narrativ.config.MarpokatProperties;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.response.ChatTokenResponse;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.ProfileImageUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hands the apps what they need to connect to Marpokat (marpokat PLAN NV-03). The user is upserted first: Marpokat
 * only accepts tokens of users it knows, and this keeps the chat profile in step with the Narrativ profile.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatTokenService {

    private final MarpokatProperties properties;
    private final MarpokatClient marpokat;
    private final UserRepository userRepository;

    public ChatTokenResponse issueToken(UUID userId) {
        if (!properties.enabled()) {
            log.warn("CHAT.TOKEN.DISABLED reason=app.marpokat.* not configured");
            throw new BusinessException(GlobalMessage.CHAT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        UserEntity user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        String externalId = user.getId().toString();
        try {
            marpokat.upsertUser(externalId, profileOf(user));
        } catch (MarpokatClient.MarpokatException e) {
            log.error("CHAT.TOKEN.UPSERT.FAILED userId={} reason={}", userId, e.getMessage());
            throw new BusinessException(GlobalMessage.CHAT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        MarpokatClient.SignedToken token = marpokat.userToken(externalId);
        return new ChatTokenResponse(token.token(), token.expiresAt(), properties.apiKey(), properties.baseUrl(), externalId);
    }

    /**
     * The profile Marpokat shows to other chat members. Uploaded profile pictures are only reachable through presigned
     * URLs that expire within an hour, so they are not sent: the apps show those from the Narrativ profile, found with
     * metadata.username. External pictures (Google) do not expire and are sent as they are.
     */
    static MarpokatClient.UserProfile profileOf(UserEntity user) {
        String name = String.join(" ", nonBlank(user.getFirstName()), nonBlank(user.getLastName())).trim();
        if (name.isEmpty()) {
            name = user.getUsername();
        }
        String stored = user.getProfileImage();
        String avatar = stored != null && ProfileImageUrlResolver.isExternalUrl(stored) ? stored : null;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("username", user.getUsername());
        return new MarpokatClient.UserProfile(name, avatar, metadata);
    }

    private static String nonBlank(String value) {
        return value == null ? "" : value.trim();
    }
}
