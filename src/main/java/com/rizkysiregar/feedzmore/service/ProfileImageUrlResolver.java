package com.rizkysiregar.feedzmore.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Turns what {@code users.profile_image} holds into a URL a client can load. The column is either an external
 * http(s) URL (a Google picture, or a value from registration) which is returned as is, or an object key of an
 * uploaded picture which is presigned. A failed presign yields {@code null} so one bad object cannot break a list.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProfileImageUrlResolver {

    private final FileStorageService fileStorageService;

    public String resolve(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (isExternalUrl(stored)) {
            return stored;
        }
        try {
            return fileStorageService.getPresignUrl(stored);
        } catch (Exception e) {
            log.warn("PROFILE.IMAGE.PRESIGN.FAILED key={} reason={}", stored, e.getMessage());
            return null;
        }
    }

    public static boolean isExternalUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
