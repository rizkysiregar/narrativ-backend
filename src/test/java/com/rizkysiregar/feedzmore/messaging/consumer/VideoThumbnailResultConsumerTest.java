package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.messaging.event.VideoThumbnailGeneratedEvent;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoThumbnailResultConsumerTest {

    private static final String MEDIA_KEY = "user/POST_FEED/video.mp4";
    private static final String THUMB_KEY = "user/POST_FEED/video_thumb.jpg";

    @Mock
    private PostCrudRepository postCrudRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private VideoThumbnailResultConsumer consumer;

    @Test
    void ignoresIncompleteEvent() {
        consumer.handleThumbnailGenerated(VideoThumbnailGeneratedEvent.builder()
                .postId(UUID.randomUUID())
                .build());

        verifyNoInteractions(postCrudRepository, fileStorageService);
    }

    @Test
    void savesThumbnailOnPost() {
        UUID postId = UUID.randomUUID();
        PostEntity post = PostEntity.builder().id(postId).mediaUrl(MEDIA_KEY).build();
        when(postCrudRepository.findById(postId)).thenReturn(Optional.of(post));

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(postId, THUMB_KEY));

        assertEquals(THUMB_KEY, post.getThumbnailUrl());
        verify(postCrudRepository).save(post);
    }

    @Test
    void deletesOrphanThumbnailWhenPostIsMissing() {
        UUID postId = UUID.randomUUID();
        when(postCrudRepository.findById(postId)).thenReturn(Optional.empty());

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(postId, THUMB_KEY));

        verify(fileStorageService).deleteObject(THUMB_KEY);
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void deletesOrphanThumbnailWhenPostIsDeleted() {
        UUID postId = UUID.randomUUID();
        PostEntity post = PostEntity.builder().id(postId).mediaUrl(MEDIA_KEY).isDeleted(true).build();
        when(postCrudRepository.findById(postId)).thenReturn(Optional.of(post));

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(postId, THUMB_KEY));

        verify(fileStorageService).deleteObject(THUMB_KEY);
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void rejectsUnexpectedThumbnailKey() {
        UUID postId = UUID.randomUUID();
        PostEntity post = PostEntity.builder().id(postId).mediaUrl(MEDIA_KEY).build();
        when(postCrudRepository.findById(postId)).thenReturn(Optional.of(post));

        consumer.handleThumbnailGenerated(
                new VideoThumbnailGeneratedEvent(postId, "someone-else/POST_FEED/x_thumb.jpg"));

        verify(postCrudRepository, never()).save(any());
        verify(fileStorageService, never()).deleteObject(any());
    }
}
