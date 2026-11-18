package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.ObjectStat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * Reads the size and content type of an object a client uploaded with a presigned PUT. A presigned PUT cannot cap
 * the size, so this check is what keeps a 2 GB "image" out of a post or a profile. The HEAD behind it sometimes
 * fails transiently on the way to object storage, so it is retried a few times; when it never answers the upload is
 * refused (503, the client can try again) instead of being accepted unchecked. A missing object (404) is final.
 */
@Slf4j
public final class UploadedObjectStats {

    static final int ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 150;

    private UploadedObjectStats() {
    }

    public static ObjectStat statOrRefuse(FileStorageService fileStorageService, String objectKey) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return fileStorageService.statObject(objectKey);
            } catch (BusinessException e) {
                if (HttpStatus.NOT_FOUND.equals(e.getHttpStatus())) {
                    throw e;
                }
                log.warn("UPLOAD.STAT.FAILED key={} attempt={}/{} reason={}", objectKey, attempt, ATTEMPTS, e.getMessage());
            } catch (RuntimeException e) {
                log.warn("UPLOAD.STAT.FAILED key={} attempt={}/{} reason={}", objectKey, attempt, ATTEMPTS, e.getMessage());
            }
            if (attempt < ATTEMPTS) {
                pause();
            }
        }
        log.error("UPLOAD.STAT.UNAVAILABLE key={}", objectKey);
        throw new BusinessException(GlobalMessage.UPLOAD_NOT_VERIFIED, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
