package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.repository.UserBlockRepository;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.response.FollowStatusResponse;
import com.rizkysiregar.feedzmore.model.response.FollowUserResponse;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.FollowRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.impl.FollowServiceImpl;
import com.rizkysiregar.feedzmore.util.Operation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {
    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private ProfileImageUrlResolver profileImageUrlResolver;

    @InjectMocks
    private FollowServiceImpl followService;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @Test
    void followInsertsRequesterToTargetWithUuid() {
        activeUsers();

        assertEquals(Operation.CREATED, followService.follow(targetId, requesterId));

        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        verify(followRepository).insertIfAbsent(id.capture(), org.mockito.ArgumentMatchers.eq(requesterId),
                org.mockito.ArgumentMatchers.eq(targetId));
        assertNotNull(id.getValue());
    }

    @Test
    void followAlreadyPresentIsIdempotent() {
        activeUsers();
        when(followRepository.insertIfAbsent(any(UUID.class), org.mockito.ArgumentMatchers.eq(requesterId),
                org.mockito.ArgumentMatchers.eq(targetId))).thenReturn(0);

        assertEquals(Operation.CREATED, followService.follow(targetId, requesterId));
        verify(followRepository).insertIfAbsent(any(UUID.class), org.mockito.ArgumentMatchers.eq(requesterId),
                org.mockito.ArgumentMatchers.eq(targetId));
    }

    @Test
    void followRejectsSelf() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> followService.follow(requesterId, requesterId));

        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verifyNoInteractions(userRepository, followRepository);
    }

    @Test
    void followRejectsDeletedRequester() {
        when(userRepository.findActiveById(requesterId)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> followService.follow(targetId, requesterId));

        assertEquals(GlobalMessage.USER_NOT_FOUND.getMessage(), error.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
        verify(userRepository, never()).findActiveById(targetId);
        verifyNoInteractions(followRepository);
    }

    @Test
    void followRejectsDeletedTarget() {
        when(userRepository.findActiveById(requesterId)).thenReturn(Optional.of(new UserEntity()));
        when(userRepository.findActiveById(targetId)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> followService.follow(targetId, requesterId));

        assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
        verifyNoInteractions(followRepository);
    }

    @Test
    void unfollowIsIdempotentEvenWhenNoRelationshipExists() {
        activeUsers();
        when(followRepository.deleteByFollowerIdAndFollowedId(requesterId, targetId)).thenReturn(0);

        assertEquals(Operation.DELETED, followService.unfollow(targetId, requesterId));
        verify(followRepository).deleteByFollowerIdAndFollowedId(requesterId, targetId);
    }

    @Test
    void unfollowRejectsInactiveTarget() {
        when(userRepository.findActiveById(requesterId)).thenReturn(Optional.of(new UserEntity()));

        assertThrows(BusinessException.class, () -> followService.unfollow(targetId, requesterId));
        verifyNoInteractions(followRepository);
    }

    @Test
    void followersArePaginatedPublicProfilesOnly() {
        activeUsers();
        FollowUserResponse follower = new FollowUserResponse(UUID.randomUUID(), "alice", "Alice", "A", "image.jpg");
        PageRequest pageable = PageRequest.of(1, 2);
        when(followRepository.findActiveFollowers(targetId, requesterId, pageable))
                .thenReturn(new PageImpl<>(List.of(follower), pageable, 3));
        when(profileImageUrlResolver.resolve("image.jpg")).thenReturn("https://signed/image.jpg");

        PaginatedResponse<FollowUserResponse> result = followService.getFollowers(targetId, requesterId, 1, 2);

        assertEquals(List.of(follower.withProfileImage("https://signed/image.jpg")), result.getContent());
        assertEquals(1, result.getPageNumber());
        assertEquals(2, result.getPageSize());
        assertEquals(3, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertTrue(result.isLast());
        verify(followRepository).findActiveFollowers(targetId, requesterId, pageable);
    }

    @Test
    void followingIsZeroBasedAndPaginated() {
        activeUsers();
        PageRequest pageable = PageRequest.of(0, 20);
        when(followRepository.findActiveFollowing(targetId, requesterId, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PaginatedResponse<FollowUserResponse> result = followService.getFollowing(targetId, requesterId, 0, 20);

        assertEquals(0, result.getPageNumber());
        assertEquals(List.of(), result.getContent());
        verify(followRepository).findActiveFollowing(targetId, requesterId, pageable);
    }

    @Test
    void invalidPaginationIsRejectedBeforeQuery() {
        activeUsers();

        BusinessException error = assertThrows(BusinessException.class,
                () -> followService.getFollowers(targetId, requesterId, -1, 20));

        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertThrows(BusinessException.class, () -> followService.getFollowing(targetId, requesterId, 0, 0));
        assertThrows(BusinessException.class, () -> followService.getFollowers(targetId, requesterId, 0, 101));
        verifyNoInteractions(followRepository);
    }

    @Test
    void inactiveRequesterCannotReadLists() {
        when(userRepository.findActiveById(requesterId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> followService.getFollowers(targetId, requesterId, 0, 20));
        verifyNoInteractions(followRepository);
    }

    @Test
    void followStatusUsesRequesterAndActiveCountsForTarget() {
        activeUsers();
        when(followRepository.existsActiveFollow(requesterId, targetId)).thenReturn(true);
        when(followRepository.countActiveFollowers(targetId)).thenReturn(4L);
        when(followRepository.countActiveFollowing(targetId)).thenReturn(2L);

        FollowStatusResponse result = followService.getFollowStatus(targetId, requesterId);

        assertTrue(result.isFollowing());
        assertEquals(4, result.followerCount());
        assertEquals(2, result.followingCount());
    }

    @Test
    void followStatusReportsNotFollowing() {
        activeUsers();

        FollowStatusResponse result = followService.getFollowStatus(targetId, requesterId);

        assertFalse(result.isFollowing());
        assertEquals(0, result.followerCount());
        assertEquals(0, result.followingCount());
    }

    private void activeUsers() {
        when(userRepository.findActiveById(requesterId)).thenReturn(Optional.of(new UserEntity()));
        when(userRepository.findActiveById(targetId)).thenReturn(Optional.of(new UserEntity()));
    }
}
