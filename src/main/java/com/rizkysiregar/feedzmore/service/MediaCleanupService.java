package com.rizkysiregar.feedzmore.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deletes object storage files outside the request that asked for it, once the database change is committed.
 * The rows that pointed at these files are already gone by then, so a file that is not removed (storage hiccup,
 * full queue, shutdown) is an orphan for {@link OrphanMediaCleanupService}, never a file still linked to a user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupService {

    private final FileStorageService fileStorageService;

    @Async("mediaCleanupExecutor")
    public void deleteObjects(List<String> objectKeys) {
        // FileStorageService logs each file it cannot delete
        objectKeys.forEach(fileStorageService::deleteObject);
        log.info("MEDIA.CLEANUP.DONE: files={}", objectKeys.size());
    }
}
