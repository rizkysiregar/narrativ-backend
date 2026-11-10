package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.ObjectStorageTypeGroup;
import com.rizkysiregar.feedzmore.model.dto.StoredObject;
import com.rizkysiregar.feedzmore.repository.PostMediaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Removes post media that was uploaded through a presigned URL but never became part of a post (the user gave up,
 * or the post was rejected). An object under {@code owner/POST_FEED/} that no {@code post_media} row uses as media
 * or thumbnail, and that is older than the grace period, is an orphan. The grace period must stay well above the
 * presigned upload expiry (15 min) and the time the media worker needs for a thumbnail.
 * <p>
 * Off by default. With {@code dry-run} on it only logs what it would delete, and one run never deletes more than
 * {@code max-deletes-per-run}, so a misconfigured database cannot wipe the bucket in one go.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.storage.orphan-cleanup", name = "enabled", havingValue = "true")
public class OrphanMediaCleanupService {

    private static final int BATCH_SIZE = 500;

    private final FileStorageService fileStorageService;
    private final PostMediaRepository postMediaRepository;
    private final Duration grace;
    private final boolean dryRun;
    private final int maxDeletesPerRun;

    public OrphanMediaCleanupService(FileStorageService fileStorageService,
                                     PostMediaRepository postMediaRepository,
                                     @Value("${app.storage.orphan-cleanup.grace:PT24H}") Duration grace,
                                     @Value("${app.storage.orphan-cleanup.dry-run:true}") boolean dryRun,
                                     @Value("${app.storage.orphan-cleanup.max-deletes-per-run:1000}") int maxDeletesPerRun) {
        this.fileStorageService = fileStorageService;
        this.postMediaRepository = postMediaRepository;
        this.grace = grace;
        this.dryRun = dryRun;
        this.maxDeletesPerRun = maxDeletesPerRun;
    }

    @Scheduled(initialDelayString = "${app.storage.orphan-cleanup.initial-delay:PT10M}",
            fixedDelayString = "${app.storage.orphan-cleanup.interval:PT6H}")
    public void run() {
        try {
            sweep(Instant.now());
        } catch (Exception e) {
            log.error("ORPHAN.MEDIA.SWEEP.FAILED: {}", e.getMessage(), e);
        }
    }

    /** Returns how many orphans were found (and deleted, unless dry-run). */
    public int sweep(Instant now) {
        Instant cutoff = now.minus(grace);
        int found = 0;
        List<String> batch = new ArrayList<>(BATCH_SIZE);
        try (Stream<StoredObject> objects = fileStorageService.listObjects("")) {
            Iterator<StoredObject> it = objects.iterator();
            while (it.hasNext() && found < maxDeletesPerRun) {
                StoredObject object = it.next();
                if (isPostMedia(object.key()) && object.lastModified().isBefore(cutoff)) {
                    batch.add(object.key());
                }
                if (batch.size() >= BATCH_SIZE) {
                    found += handle(batch, maxDeletesPerRun - found);
                    batch.clear();
                }
            }
            if (!batch.isEmpty() && found < maxDeletesPerRun) {
                found += handle(batch, maxDeletesPerRun - found);
            }
        }
        log.info("ORPHAN.MEDIA.SWEEP.DONE orphans={} dryRun={} cap={}", found, dryRun, maxDeletesPerRun);
        return found;
    }

    private int handle(List<String> candidates, int budget) {
        Set<String> used = new HashSet<>(postMediaRepository.findReferencedMediaKeys(candidates));
        used.addAll(postMediaRepository.findReferencedThumbnailKeys(candidates));
        int handled = 0;
        for (String key : candidates) {
            if (used.contains(key)) {
                continue;
            }
            if (handled >= budget) {
                break;
            }
            handled++;
            if (dryRun) {
                log.info("ORPHAN.MEDIA.WOULD.DELETE {}", key);
            } else {
                fileStorageService.deleteObject(key);
            }
        }
        return handled;
    }

    // owner/POST_FEED/file
    private boolean isPostMedia(String key) {
        String[] segments = key.split("/", 3);
        return segments.length == 3 && ObjectStorageTypeGroup.POST_FEED.equals(segments[1]);
    }
}
