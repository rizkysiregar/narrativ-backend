package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.response.FollowUserResponse;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.FollowRepository;
import com.rizkysiregar.feedzmore.repository.UserBlockRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import com.rizkysiregar.feedzmore.util.Paging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * A block works in both directions: the two users no longer see each other's posts, comments, profile, nor find
 * each other in search and follow lists. Blocking also ends any follow between them.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserBlockService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    @Transactional
    public Operation block(UUID blockerId, UUID targetId) {
        if (blockerId.equals(targetId)) {
            throw new BusinessException(GlobalMessage.CANNOT_BLOCK_SELF, HttpStatus.BAD_REQUEST);
        }
        userRepository.findActiveById(targetId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        userBlockRepository.insertIfAbsent(UUID.randomUUID(), blockerId, targetId);
        followRepository.deleteByFollowerIdAndFollowedId(blockerId, targetId);
        followRepository.deleteByFollowerIdAndFollowedId(targetId, blockerId);
        log.info("USER.BLOCKED: blocker={} blocked={}", blockerId, targetId);
        return Operation.CREATED;
    }

    @Transactional
    public Operation unblock(UUID blockerId, UUID targetId) {
        userBlockRepository.deleteByPair(blockerId, targetId);
        return Operation.DELETED;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<FollowUserResponse> getBlockedUsers(UUID blockerId, int page, int size) {
        Page<FollowUserResponse> blocked = userBlockRepository
                .findBlockedUsers(blockerId, Paging.of(page, size, MAX_PAGE_SIZE));
        List<FollowUserResponse> users = blocked.getContent().stream()
                .map(user -> user.withProfileImage(profileImageUrlResolver.resolve(user.profileImage())))
                .toList();
        return new PaginatedResponse<>(users, blocked.getNumber(), blocked.getSize(),
                blocked.getTotalElements(), blocked.getTotalPages(), blocked.isLast());
    }
}
