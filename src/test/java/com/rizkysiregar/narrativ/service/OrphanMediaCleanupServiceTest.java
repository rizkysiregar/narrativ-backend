package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.dto.StoredObject;
import com.rizkysiregar.narrativ.repository.PostMediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanMediaCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant OLD = NOW.minus(Duration.ofHours(48));
    private static final Instant FRESH = NOW.minus(Duration.ofHours(1));

    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private PostMediaRepository postMediaRepository;

    private OrphanMediaCleanupService service(boolean dryRun, int cap) {
        return new OrphanMediaCleanupService(fileStorageService, postMediaRepository, Duration.ofHours(24), dryRun, cap);
    }

    @BeforeEach
    void nothingIsReferencedByDefault() {
        org.mockito.Mockito.lenient().when(postMediaRepository.findReferencedMediaKeys(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(postMediaRepository.findReferencedThumbnailKeys(any())).thenReturn(List.of());
    }

    private void listing(StoredObject... objects) {
        when(fileStorageService.listObjects("")).thenReturn(Stream.of(objects));
    }

    @Test
    void deletesOldUnreferencedPostMediaOnly() {
        listing(new StoredObject("alice/POST_FEED/orphan.jpg", OLD),
                new StoredObject("alice/POST_FEED/used.jpg", OLD),
                new StoredObject("alice/POST_FEED/used_thumb.jpg", OLD),
                new StoredObject("alice/POST_FEED/fresh.jpg", FRESH),
                new StoredObject("alice/PROFILE_PICTURE/avatar.png", OLD),
                new StoredObject("stray.txt", OLD));
        when(postMediaRepository.findReferencedMediaKeys(any())).thenReturn(List.of("alice/POST_FEED/used.jpg"));
        when(postMediaRepository.findReferencedThumbnailKeys(any())).thenReturn(List.of("alice/POST_FEED/used_thumb.jpg"));

        int found = service(false, 100).sweep(NOW);

        assertEquals(1, found);
        verify(fileStorageService).deleteObject("alice/POST_FEED/orphan.jpg");
        verify(fileStorageService, never()).deleteObject("alice/POST_FEED/used.jpg");
        verify(fileStorageService, never()).deleteObject("alice/POST_FEED/used_thumb.jpg");
        verify(fileStorageService, never()).deleteObject("alice/POST_FEED/fresh.jpg");
        verify(fileStorageService, never()).deleteObject("alice/PROFILE_PICTURE/avatar.png");
        verify(fileStorageService, never()).deleteObject("stray.txt");
    }

    @Test
    void dryRunOnlyReports() {
        listing(new StoredObject("alice/POST_FEED/orphan.jpg", OLD));

        int found = service(true, 100).sweep(NOW);

        assertEquals(1, found);
        verify(fileStorageService, never()).deleteObject(any());
    }

    @Test
    void neverDeletesMoreThanTheCapInOneRun() {
        listing(new StoredObject("a/POST_FEED/1.jpg", OLD), new StoredObject("a/POST_FEED/2.jpg", OLD),
                new StoredObject("a/POST_FEED/3.jpg", OLD));

        int found = service(false, 2).sweep(NOW);

        assertEquals(2, found);
        verify(fileStorageService).deleteObject("a/POST_FEED/1.jpg");
        verify(fileStorageService).deleteObject("a/POST_FEED/2.jpg");
        verify(fileStorageService, never()).deleteObject("a/POST_FEED/3.jpg");
    }

    @Test
    void emptyBucketTouchesNothing() {
        listing();

        assertEquals(0, service(false, 100).sweep(NOW));
        verify(postMediaRepository, never()).findReferencedMediaKeys(any());
    }
}
