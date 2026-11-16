package com.rizkysiregar.narrativ.service.impl;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.response.FollowStatusResponse;
import com.rizkysiregar.narrativ.model.response.FollowUserResponse;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.repository.FollowRepository;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.FollowService;
import com.rizkysiregar.narrativ.service.ProfileImageUrlResolver;
import com.rizkysiregar.narrativ.util.Operation;
import com.rizkysiregar.narrativ.util.Paging;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    // follower lists are cheap rows, so they may be paged wider than the feeds
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    @Transactional
    public Operation follow(UUID userId, UUID requesterId) {
        if (userId.equals(requesterId)) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        requireActiveUsers(userId, requesterId);
        followRepository.insertIfAbsent(UUID.randomUUID(), requesterId, userId);
        return Operation.CREATED;
    }

    @Override
    @Transactional
    public Operation unfollow(UUID userId, UUID requesterId) {
        requireActiveUsers(userId, requesterId);
        followRepository.deleteByFollowerIdAndFollowedId(requesterId, userId);
        return Operation.DELETED;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<FollowUserResponse> getFollowers(UUID userId, UUID requesterId, int page, int size) {
        requireActiveUsers(userId, requesterId);
        return toResponse(followRepository.findActiveFollowers(userId, requesterId, pageable(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<FollowUserResponse> getFollowing(UUID userId, UUID requesterId, int page, int size) {
        requireActiveUsers(userId, requesterId);
        return toResponse(followRepository.findActiveFollowing(userId, requesterId, pageable(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public FollowStatusResponse getFollowStatus(UUID userId, UUID requesterId) {
        requireActiveUsers(userId, requesterId);
        return new FollowStatusResponse(
                followRepository.existsActiveFollow(requesterId, userId),
                followRepository.countActiveFollowers(userId),
                followRepository.countActiveFollowing(userId)
        );
    }

    private void requireActiveUsers(UUID userId, UUID requesterId) {
        userRepository.findActiveById(requesterId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        // a user on either side of a block does not exist for the other
        if (userBlockRepository.existsBetween(requesterId, userId)) {
            throw new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
    }

    private PageRequest pageable(int page, int size) {
        return Paging.of(page, size, MAX_PAGE_SIZE);
    }

    private PaginatedResponse<FollowUserResponse> toResponse(Page<FollowUserResponse> results) {
        List<FollowUserResponse> users = results.getContent().stream()
                .map(user -> user.withProfileImage(profileImageUrlResolver.resolve(user.profileImage())))
                .toList();
        return new PaginatedResponse<>(users, results.getNumber(), results.getSize(),
                results.getTotalElements(), results.getTotalPages(), results.isLast());
    }
}
