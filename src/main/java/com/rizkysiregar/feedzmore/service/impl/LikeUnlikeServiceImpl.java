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
    public Operation likePost(LikeUnlikePostRequest request) {
        log.info("## User " + request.getUsername() + " trying to like post " + request.getPostId());
//        Optional<UserEntity> user = userRepository.findById(request.getUserId());
        Optional<UserEntity> user = userRepository.findByUsername(request.getUsername());
        Optional<PostEntity> post = postCrudRepository.findById(request.getPostId());

        if (user.isEmpty()) {
            log.error("::. user with id " + request.getUsername() + " not found");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        if (post.isEmpty()) {
            log.error("::. post with id " + request.getPostId() + " not found");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        if (!likeRepository.existsByUserIdAndPostId(user.get().getId(), request.getPostId())) {
            LikeEntity likeEntity = new LikeEntity();
            likeEntity.setUser(user.get());
            likeEntity.setPost(post.get());
            likeEntity.setCreatedAt(LocalDateTime.now());
            likeRepository.save(likeEntity);
        }

        return Operation.LIKED;
    }

    @Transactional
    @Override
    public Operation unlikePost(UUID postId, UUID userId) {
        log.info("## User " + userId + " trying to unlike post " + postId);

        Optional<LikeEntity> likeEntity = likeRepository.findByUserIdAndPostId(userId, postId);

        if (likeEntity.isEmpty()) {
            log.error("::. there is not matched like data");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        likeRepository.deleteByUserIdAndPostId(userId, postId);
        return Operation.DISLIKED;
    }

    @Override
    public PostLikeCountDTO getPostLikeCount(UUID postId) {
        log.info("## Tyring to get like count of post " + postId);

        return PostLikeCountDTO.builder()
                .likesCount(likeRepository.countByPostId(postId))
                .build();
    }
}
