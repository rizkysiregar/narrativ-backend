package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Single place that words the "your account is banned" answer, so login, refresh and every
 * authenticated request tell the user the same thing, including the reason the admin gave.
 */
public final class BanMessages {

    private BanMessages() {
    }

    public static BusinessException bannedException(String reason) {
        if (reason == null || reason.isBlank()) {
            return new BusinessException(GlobalMessage.USER_BANNED, HttpStatus.UNAUTHORIZED);
        }
        return new BusinessException(GlobalMessage.USER_BANNED_REASON, HttpStatus.UNAUTHORIZED, reason.trim());
    }

    public static String bannedMessage(String reason) {
        return bannedException(reason).getLocalizedMessage();
    }
}
