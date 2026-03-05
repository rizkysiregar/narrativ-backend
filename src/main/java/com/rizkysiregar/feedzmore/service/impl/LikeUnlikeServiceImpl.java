package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.model.entity.LikeEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.LikeUnlikePostRequest;
import com.rizkysiregar.feedzmore.repository.LikeRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.LikeUnlikeService;
import com.rizkysiregar.feedzmore.util.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LikeUnlikeServiceImpl implements LikeUnlikeService {

    private final LikeRepository likeRepository;
    private final UserRepository userRepository;
    private final PostCrudRepository postCrudRepository;

    @Override
    public Operation likePost(LikeUnlikePostRequest request, UUID userId) {
        log.info("## User {} trying to like post {}", userId, request.getPostId());

        if (!userRepository.existsById(userId)) {
            log.error("::. user with id " + userId + " not found");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        if (!postCrudRepository.existsById(request.getPostId())) {
            log.error("::. post with id " + request.getPostId() + " not found");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        try {
            if (!likeRepository.existsByUserIdAndPostId(userId, request.getPostId())) {
                LikeEntity likeEntity = new LikeEntity();
                likeEntity.setUser(userRepository.getReferenceById(userId));
                likeEntity.setPost(postCrudRepository.getReferenceById(request.getPostId()));
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
        log.info("## User {} trying to unlike post {}", userId, postId);
        int deletedCount = likeRepository.deleteDirectly(userId, postId);

        if (deletedCount == 0) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        return Operation.DISLIKED;
    }

    @Override
    public PostLikeCountDTO getLikePostCount(UUID postId) {
        log.info("## Tyring to get like count of post {}", postId);

        return PostLikeCountDTO.builder()
                .likesCount(likeRepository.countByPostId(postId))
                .build();
    }
}
