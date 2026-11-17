package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.messaging.event.VideoThumbnailGeneratedEvent;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.PostMediaEntity;
import com.rizkysiregar.narrativ.model.enums.MediaType;
import com.rizkysiregar.narrativ.repository.PostCrudRepository;
import com.rizkysiregar.narrativ.repository.PostMediaRepository;
import com.rizkysiregar.narrativ.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private PostMediaRepository postMediaRepository;
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

    private static PostMediaEntity video(UUID postId, String key) {
        return PostMediaEntity.builder().id(UUID.randomUUID()).postId(postId).position(0)
                .mediaKey(key).mediaType(MediaType.VIDEO).build();
    }

    private static PostEntity post(UUID postId, boolean deleted) {
        return PostEntity.builder().id(postId).isDeleted(deleted).build();
    }

    @Test
    void savesThumbnailOnTheMedia() {
        UUID postId = UUID.randomUUID();
        PostMediaEntity item = video(postId, MEDIA_KEY);
        when(postCrudRepository.findById(postId)).thenReturn(Optional.of(post(postId, false)));
        when(postMediaRepository.findById(item.getId())).thenReturn(Optional.of(item));

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(postId, item.getId(), THUMB_KEY));

        assertEquals(THUMB_KEY, item.getThumbnailKey());
        verify(postMediaRepository).save(item);
    }

    @Test
    void picksTheVideoByThumbnailKeyWhenTheWorkerDoesNotEchoTheMediaId() {
        UUID postId = UUID.randomUUID();
        PostMediaEntity other = video(postId, "user/POST_FEED/other.mp4");
        PostMediaEntity item = video(postId, MEDIA_KEY);
        when(postCrudRepository.findById(postId)).thenReturn(Optional.of(post(postId, false)));
        when(postMediaRepository.findByPostIdOrderByPositionAsc(postId)).thenReturn(List.of(other, item));

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(postId, null, THUMB_KEY));

        assertEquals(THUMB_KEY, item.getThumbnailKey());
        assertNull(other.getThumbnailKey());
        verify(postMediaRepository).save(item);
    }

    @Test
    void deletesOrphanThumbnailWhenPostIsMissing() {
        UUID postId = UUID.randomUUID();
        when(postCrudRepository.findById(postId)).thenReturn(Optional.empty());

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(postId, UUID.randomUUID(), THUMB_KEY));

        verify(fileStorageService).deleteObject(THUMB_KEY);
        verify(postMediaRepository, never()).save(any());
    }

    @Test
    void deletesOrphanThumbnailWhenPostIsDeleted() {
        UUID postId = UUID.randomUUID();
        when(postCrudRepository.findById(postId)).thenReturn(Optional.of(post(postId, true)));

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(postId, UUID.randomUUID(), THUMB_KEY));

        verify(fileStorageService).deleteObject(THUMB_KEY);
        verify(postMediaRepository, never()).save(any());
    }

    @Test
    void rejectsUnexpectedThumbnailKey() {
        UUID postId = UUID.randomUUID();
        PostMediaEntity item = video(postId, MEDIA_KEY);
        when(postCrudRepository.findById(postId)).thenReturn(Optional.of(post(postId, false)));
        when(postMediaRepository.findById(item.getId())).thenReturn(Optional.of(item));

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(
                postId, item.getId(), "someone-else/POST_FEED/x_thumb.jpg"));

        verify(postMediaRepository, never()).save(any());
        verify(fileStorageService, never()).deleteObject(any());
    }

    @Test
    void rejectsAMediaThatBelongsToAnotherPost() {
        UUID postId = UUID.randomUUID();
        PostMediaEntity item = video(UUID.randomUUID(), MEDIA_KEY);
        when(postCrudRepository.findById(postId)).thenReturn(Optional.of(post(postId, false)));
        when(postMediaRepository.findById(item.getId())).thenReturn(Optional.of(item));

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(postId, item.getId(), THUMB_KEY));

        verify(postMediaRepository, never()).save(any());
    }

    @Test
    void rejectsAThumbnailForAnImage() {
        UUID postId = UUID.randomUUID();
        PostMediaEntity item = video(postId, MEDIA_KEY);
        item.setMediaType(MediaType.IMAGE);
        when(postCrudRepository.findById(postId)).thenReturn(Optional.of(post(postId, false)));
        when(postMediaRepository.findById(item.getId())).thenReturn(Optional.of(item));

        consumer.handleThumbnailGenerated(new VideoThumbnailGeneratedEvent(postId, item.getId(), THUMB_KEY));

        verify(postMediaRepository, never()).save(any());
    }
}
