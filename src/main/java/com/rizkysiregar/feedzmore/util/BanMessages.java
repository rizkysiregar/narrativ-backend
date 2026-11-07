package com.rizkysiregar.feedzmore.util;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
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
        return new BusinessException(GlobalMessage.USER_BANNED, "Alasan: " + reason.trim(), HttpStatus.UNAUTHORIZED);
    }

    public static String bannedMessage(String reason) {
        return bannedException(reason).getMessage();
    }
}
