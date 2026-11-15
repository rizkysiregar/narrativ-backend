package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.model.entity.LikeEntity;
import com.rizkysiregar.feedzmore.repository.LikeRepository;
import com.rizkysiregar.feedzmore.repository.UserBlockRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.LikeDislikeService;
import com.rizkysiregar.feedzmore.util.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LikeDislikeServiceImpl implements LikeDislikeService {

    private final LikeRepository likeRepository;
    private final UserRepository userRepository;
    private final PostCrudRepository postCrudRepository;
    private final UserBlockRepository userBlockRepository;

    @Override
    public Operation likePost(UUID postId, UUID userId) {
        log.debug("POST.LIKE userId={} postId={}", userId, postId);

        if (!userRepository.existsById(userId)) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        // a post of someone on either side of a block answers like a post that is gone
        boolean likeable = postCrudRepository.findInteractableById(postId)
                .map(post -> !userBlockRepository.existsBetween(userId, post.getUser().getId()))
                .orElse(false);
        if (!likeable) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        try {
            if (!likeRepository.existsByUserIdAndPostId(userId, postId)) {
                LikeEntity likeEntity = new LikeEntity();
                likeEntity.setUser(userRepository.getReferenceById(userId));
                likeEntity.setPost(postCrudRepository.getReferenceById(postId));
                likeEntity.setCreatedAt(LocalDateTime.now());
                likeRepository.save(likeEntity);
            }
        } catch (DataIntegrityViolationException e) {
            // Duplicate entry - race condition
            return Operation.LIKED;
        }

        return Operation.LIKED;
    }

    @Transactional
    @Override
    public Operation dislikePost(UUID postId, UUID userId) {
        log.debug("POST.DISLIKE userId={} postId={}", userId, postId);
        int deletedCount = likeRepository.deleteDirectly(userId, postId);

        if (deletedCount == 0) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        return Operation.DISLIKED;
    }

    @Override
    public PostLikeCountDTO getLikePostCount(UUID postId) {
        log.debug("POST.LIKE.COUNT postId={}", postId);
        if (!postCrudRepository.existsInteractableById(postId)) {
            throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return PostLikeCountDTO.builder()
                .likesCount(likeRepository.countByPostId(postId))
                .build();
    }
}
