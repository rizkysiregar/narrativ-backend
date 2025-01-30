package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.model.entity.LikeEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.LikeUnlikePostRequest;
import com.rizkysiregar.feedzmore.model.response.LikesDTO;
import com.rizkysiregar.feedzmore.repository.LikeRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.LikeUnlikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
    public LikesDTO likePost(LikeUnlikePostRequest request) {
        log.info("## User " + request.getUserId() + " trying to like post " + request.getPostId());
        Optional<UserEntity> user = userRepository.findById(request.getUserId());
        Optional<PostEntity> post = postCrudRepository.findById(request.getPostId());

        if (user.isEmpty()) {
            log.error("::. user with id " + request.getUserId() + " not found");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        if (post.isEmpty()) {
            log.error("::. post with id " + request.getPostId() + " not found");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        if (!likeRepository.existsByUserIdAndPostId(request.getUserId(), request.getPostId())) {
            LikeEntity likeEntity = new LikeEntity();
            likeEntity.setUser(user.get());
            likeEntity.setPost(post.get());
            likeEntity.setCreatedAt(LocalDateTime.now());
            likeRepository.save(likeEntity);
        }

        return LikesDTO.builder()
                .status(GlobalMessage.SUCCESS.getMessage())
                .build();
    }

    @Override
    public LikesDTO unlikePost(LikeUnlikePostRequest request) {
        log.info("## User " + request.getUserId() + " trying to unlike post " + request.getPostId());

        likeRepository.deleteByUserIdAndPostId(request.getUserId(), request.getPostId());
        return LikesDTO.builder()
                .status(GlobalMessage.SUCCESS.getMessage())
                .build();
    }

    @Override
    public PostLikeCountDTO getPostLikeCount(UUID postId) {
        log.info("## Tyring to get like count of post " + postId);
        return PostLikeCountDTO.builder()
                .likesCount(likeRepository.countByPostId(postId))
                .build();
    }
}
