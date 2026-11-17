package com.rizkysiregar.narrativ.service.impl;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.messaging.event.PostLikedEvent;
import com.rizkysiregar.narrativ.messaging.publisher.PostEventPublisher;
import com.rizkysiregar.narrativ.model.dto.PostLikeCountDTO;
import com.rizkysiregar.narrativ.model.entity.LikeEntity;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.repository.LikeRepository;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.PostCrudRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.LikeDislikeService;
import com.rizkysiregar.narrativ.util.AfterCommit;
import com.rizkysiregar.narrativ.util.Operation;
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
    private final PostEventPublisher postEventPublisher;

    @Override
    public Operation likePost(UUID postId, UUID userId) {
        log.debug("POST.LIKE userId={} postId={}", userId, postId);

        UserEntity liker = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST));

        // a post of someone on either side of a block answers like a post that is gone
        PostEntity post = postCrudRepository.findInteractableById(postId)
                .filter(found -> !userBlockRepository.existsBetween(userId, found.getUser().getId()))
                .orElseThrow(() -> new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST));

        try {
            if (!likeRepository.existsByUserIdAndPostId(userId, postId)) {
                LikeEntity likeEntity = new LikeEntity();
                likeEntity.setUser(userRepository.getReferenceById(userId));
                likeEntity.setPost(postCrudRepository.getReferenceById(postId));
                likeEntity.setCreatedAt(LocalDateTime.now());
                likeRepository.save(likeEntity);

                // only a like that was really added tells the owner; the consumer also drops repeats of an
                // unlike/like toggle
                PostLikedEvent event = PostLikedEvent.builder()
                        .postId(postId)
                        .postOwnerId(post.getUser().getId())
                        .likerId(userId)
                        .likerUsername(liker.getUsername())
                        .createdAt(likeEntity.getCreatedAt())
                        .build();
                AfterCommit.run("post liked event " + postId, () -> postEventPublisher.publishPostLiked(event));
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
