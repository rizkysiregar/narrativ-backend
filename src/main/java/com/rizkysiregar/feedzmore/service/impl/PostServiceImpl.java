package com.rizkysiregar.feedzmore.service.impl;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.constant.ObjectStorageTypeGroup;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.messaging.publisher.PostEventPublisher;
import com.rizkysiregar.feedzmore.model.dto.PostCountDTO;
import com.rizkysiregar.feedzmore.model.response.CreatePostResponse;
import com.rizkysiregar.feedzmore.model.response.PostFeedResponse;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.repository.*;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import com.rizkysiregar.feedzmore.service.PostService;
import com.rizkysiregar.feedzmore.service.VideoProcessingService;
import com.rizkysiregar.feedzmore.util.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostPaginationRepository postPaginationRepository;
    private final PostCrudRepository postCrudRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PostEventPublisher postEventPublisher;
    private final VideoProcessingService videoProcessingService;

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 50L * 1024 * 1024;
    private static final long MAX_VIDEO_DURATION_SECONDS = 60;
    private static final List<String> ALLOWED_VIDEO_TYPES = List.of("video/mp4", "video/quicktime", "video/webm");

    @Override
    public PaginatedResponse<PostFeedResponse> getExplorePosts(int page, int size, UUID userId) {
        log.info("FETCH.EXPLORE.FEED: {}, size= {} ,userId={}", page, size, userId);
        Pageable pageable = PageRequest.of(page, size);
        Page<PostEntity> postPage = postPaginationRepository.findAllActivePosts(pageable);
        return buildPaginatedResponse(postPage, userId);
    }

    @Override
    public PaginatedResponse<PostFeedResponse> getFollowingPosts(int page, int size, UUID viewerId) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        Page<PostEntity> postPage = postPaginationRepository.findFollowingPosts(PageRequest.of(page, size), viewerId);
        return buildPaginatedResponse(postPage, viewerId);
    }

    @Override
    public PaginatedResponse<PostFeedResponse> getAllPostByUserId(int page, int size, String username, UUID viewerId) {
        log.info("FETCH.FEED.BY.USERNAME =  {}, size= {} , username= {}", page, size, username);

        UserEntity user = userRepository.findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size);
        Page<PostEntity> postPage = postPaginationRepository.findAllActivePostsByUserId(pageable, user.getId());
        return buildPaginatedResponse(postPage, viewerId);
    }

    private PaginatedResponse<PostFeedResponse> buildPaginatedResponse(Page<PostEntity> postPage, UUID statusUserId) {
        List<PostEntity> posts = postPage.getContent();
        List<PostFeedResponse> content = List.of();

        if (!posts.isEmpty()) {
            List<UUID> postIds = posts.stream().map(PostEntity::getId).toList();
            Map<UUID, Long> likeCounts = likeRepository.countByPostIds(postIds).stream()
                    .collect(Collectors.toMap(PostCountDTO::postId, PostCountDTO::count));
            Map<UUID, Long> commentCounts = commentRepository.countByPostIds(postIds).stream()
                    .collect(Collectors.toMap(PostCountDTO::postId, PostCountDTO::count));
            Set<UUID> likedPostIds = new HashSet<>(likeRepository.findLikedPostIds(statusUserId, postIds));

            content = posts.stream().map(post -> {
                PostFeedResponse response = new PostFeedResponse(
                        post.getId(),
                        post.getUser().getUsername(),
                        post.getCaption(),
                        post.getMediaUrl(),
                        post.getMediaType(),
                        post.getThumbnailUrl(),
                        post.getCreatedAt(),
                        likeCounts.getOrDefault(post.getId(), 0L),
                        commentCounts.getOrDefault(post.getId(), 0L),
                        likedPostIds.contains(post.getId())
                );
                try {
                    response.setMediaUrl(fileStorageService.getPresignUrl(response.getMediaUrl()));
                    if (response.getThumbnailUrl() != null) {
                        response.setThumbnailUrl(fileStorageService.getPresignUrl(response.getThumbnailUrl()));
                    }
                } catch (Exception e) {
                    log.error("## cannot get presign url for objectName= {}, because of= {}", response.getMediaUrl(), e.getMessage());
                }
                return response;
            }).toList();
        }

        return new PaginatedResponse<>(
                content,
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages(),
                postPage.isLast());
    }

    @Override
    public CreatePostResponse addNewPost(UUID userId, AddPostRequest request, MultipartFile media) throws Exception {
        log.info("NEW.FEED.POST: {}", userId);

        UserEntity user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> {
                    log.error("Active user not found {}", userId);
                    return new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
                });

        if (media == null || media.isEmpty()) {
            log.error("Media is empty");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        String contentType = media.getContentType();
        MediaType mediaType;
        if (contentType != null && contentType.startsWith("image/")) {
            mediaType = MediaType.IMAGE;
            if (media.getSize() > MAX_IMAGE_SIZE) {
                log.error("Image too large");
                throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
            }
        } else if (contentType != null && ALLOWED_VIDEO_TYPES.contains(contentType)) {
            mediaType = MediaType.VIDEO;
            if (media.getSize() > MAX_VIDEO_SIZE) {
                log.error("Video too large");
                throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
            }
            validateVideoDuration(media);
        } else {
            log.error("Unsupported media type: {}", contentType);
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> emailProps = new HashMap<>();
        emailProps.put("username", user.getUsername());
        emailProps.put("isSuccess", true);
        emailProps.put("postTitle", request.getCaption());

        String objectName = fileStorageService.uploadFile(
                media,
                user.getUsername(),
                ObjectStorageTypeGroup.POST_FEED
        );

        PostEntity postEntity = postCrudRepository.save(
                PostEntity.builder()
                        .caption(request.getCaption())
                        .createdAt(LocalDateTime.now())
                        .mediaUrl(objectName)
                        .mediaType(mediaType)
                        .user(user)
                        .build()
        );

        // event to publish
        PostCreatedEvent event = PostCreatedEvent.builder()
                .postId(postEntity.getId())
                .authorId(user.getId())
                .username(postEntity.getUser().getUsername())
                .caption(postEntity.getCaption())
                .mediaType(postEntity.getMediaType())
                .mediaUrl(postEntity.getMediaUrl())
                .createdAt(postEntity.getCreatedAt())
                .email(user.getEmail())
                .emailProps(emailProps)
                .build();

        // broadcast post created event, currently the consumer are: notification and email
        postEventPublisher.publishPostCreatedEvent(event);


        return CreatePostResponse.builder()
                .status(GlobalMessage.CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public Operation deletePost(UUID postId, UUID userId) {
        log.info("SOFT.DELETE.FEED.POST: postId={}, userId={}", postId, userId);

        PostEntity post = postCrudRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        post.setDeleted(true);
        postCrudRepository.save(post);

        fileStorageService.deleteObject(post.getMediaUrl());
        if (post.getThumbnailUrl() != null) {
            fileStorageService.deleteObject(post.getThumbnailUrl());
        }

        return Operation.DELETED;
    }

    private void validateVideoDuration(MultipartFile media) throws Exception {
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("feedzmore-video-", ".mp4");
        try {
            try (java.io.InputStream in = media.getInputStream()) {
                java.nio.file.Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            double duration = videoProcessingService.getDurationSeconds(tempFile);
            if (duration > MAX_VIDEO_DURATION_SECONDS) {
                log.error("Video duration too long: {}s", duration);
                throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }
}
