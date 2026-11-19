package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.service.NotificationInboxService;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.messaging.event.PostLikedEvent;
import com.rizkysiregar.narrativ.messaging.publisher.PostEventPublisher;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.repository.LikeRepository;
import com.rizkysiregar.narrativ.repository.PostCrudRepository;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.impl.LikeDislikeServiceImpl;
import com.rizkysiregar.narrativ.util.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeDislikeServiceImplTest {

    @Mock
    private LikeRepository likeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostCrudRepository postCrudRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private PostEventPublisher postEventPublisher;
    @Mock
    private NotificationInboxService inboxService;

    @InjectMocks
    private LikeDislikeServiceImpl service;

    private final UUID liker = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UserEntity owner = owner();

    private static UserEntity owner() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        return user;
    }

    private UserEntity likerEntity() {
        UserEntity user = new UserEntity();
        user.setId(liker);
        user.setUsername("budi");
        return user;
    }

    @Test
    void likeWorksWhenNobodyBlockedAnybody() {
        when(userRepository.findById(liker)).thenReturn(Optional.of(likerEntity()));
        when(postCrudRepository.findInteractableById(postId))
                .thenReturn(Optional.of(PostEntity.builder().id(postId).user(owner).build()));
        when(userRepository.getReferenceById(liker)).thenReturn(new UserEntity());
        when(postCrudRepository.getReferenceById(postId)).thenReturn(PostEntity.builder().build());

        assertEquals(Operation.LIKED, service.likePost(postId, liker));

        verify(likeRepository).save(any());
    }

    @Test
    void aNewLikeTellsThePostOwnerThroughAnEvent() {
        when(userRepository.findById(liker)).thenReturn(Optional.of(likerEntity()));
        when(postCrudRepository.findInteractableById(postId))
                .thenReturn(Optional.of(PostEntity.builder().id(postId).user(owner).build()));
        when(userRepository.getReferenceById(liker)).thenReturn(new UserEntity());
        when(postCrudRepository.getReferenceById(postId)).thenReturn(PostEntity.builder().build());

        service.likePost(postId, liker);

        ArgumentCaptor<PostLikedEvent> event = ArgumentCaptor.forClass(PostLikedEvent.class);
        verify(postEventPublisher).publishPostLiked(event.capture());
        assertEquals(postId, event.getValue().getPostId());
        assertEquals(owner.getId(), event.getValue().getPostOwnerId());
        assertEquals(liker, event.getValue().getLikerId());
        assertEquals("budi", event.getValue().getLikerUsername());
    }

    @Test
    void likingAPostThatIsAlreadyLikedPublishesNothing() {
        when(userRepository.findById(liker)).thenReturn(Optional.of(likerEntity()));
        when(postCrudRepository.findInteractableById(postId))
                .thenReturn(Optional.of(PostEntity.builder().id(postId).user(owner).build()));
        when(likeRepository.existsByUserIdAndPostId(liker, postId)).thenReturn(true);

        assertEquals(Operation.LIKED, service.likePost(postId, liker));

        verify(likeRepository, never()).save(any());
        verifyNoInteractions(postEventPublisher);
    }

    @Test
    void likeOnThePostOfSomeoneBlockedAnswersLikeAMissingPost() {
        when(userRepository.findById(liker)).thenReturn(Optional.of(likerEntity()));
        when(postCrudRepository.findInteractableById(postId))
                .thenReturn(Optional.of(PostEntity.builder().id(postId).user(owner).build()));
        when(userBlockRepository.existsBetween(liker, owner.getId())).thenReturn(true);

        BusinessException blocked = assertThrows(BusinessException.class, () -> service.likePost(postId, liker));

        when(postCrudRepository.findInteractableById(postId)).thenReturn(Optional.empty());
        BusinessException missing = assertThrows(BusinessException.class, () -> service.likePost(postId, liker));

        // a block must not be told apart from a post that does not exist
        assertEquals(missing.getMessage(), blocked.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, blocked.getHttpStatus());
        verify(likeRepository, never()).save(any());
    }

    @Test
    void likeFromAnUnknownUserIsRejected() {
        when(userRepository.findById(liker)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.likePost(postId, liker));

        verifyNoInteractions(postEventPublisher);
    }

    @Test
    void unlikeTakesTheLikeOutOfTheOwnersInbox() {
        when(likeRepository.deleteDirectly(liker, postId)).thenReturn(1);

        service.dislikePost(postId, liker);

        verify(inboxService).removeLike(postId, liker);
    }
}
