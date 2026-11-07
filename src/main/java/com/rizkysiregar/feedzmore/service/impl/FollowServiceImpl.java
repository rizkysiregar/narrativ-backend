package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.response.FollowStatusResponse;
import com.rizkysiregar.feedzmore.model.response.FollowUserResponse;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.FollowRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.FollowService;
import com.rizkysiregar.feedzmore.util.Operation;
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
        return toResponse(followRepository.findActiveFollowers(userId, pageable(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<FollowUserResponse> getFollowing(UUID userId, UUID requesterId, int page, int size) {
        requireActiveUsers(userId, requesterId);
        return toResponse(followRepository.findActiveFollowing(userId, pageable(page, size)));
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
    }

    private PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        return PageRequest.of(page, size);
    }

    private PaginatedResponse<FollowUserResponse> toResponse(Page<FollowUserResponse> results) {
        return new PaginatedResponse<>(results.getContent(), results.getNumber(), results.getSize(),
                results.getTotalElements(), results.getTotalPages(), results.isLast());
    }
}
