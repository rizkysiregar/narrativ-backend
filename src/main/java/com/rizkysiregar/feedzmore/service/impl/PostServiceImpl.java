package com.rizkysiregar.feedzmore.service.impl;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.constant.ObjectStorageTypeGroup;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.messaging.event.VideoThumbnailRequestedEvent;
import com.rizkysiregar.feedzmore.messaging.publisher.PostEventPublisher;
import com.rizkysiregar.feedzmore.model.dto.PostCountDTO;
import com.rizkysiregar.feedzmore.model.dto.ObjectStat;
import com.rizkysiregar.feedzmore.model.response.CreatePostResponse;
import com.rizkysiregar.feedzmore.model.response.CreatePostUploadUrlResponse;
import com.rizkysiregar.feedzmore.model.response.PostFeedResponse;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.request.CreatePostUploadUrlRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.repository.*;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import com.rizkysiregar.feedzmore.service.PostService;
import com.rizkysiregar.feedzmore.util.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 50L * 1024 * 1024;
    private static final List<String> ALLOWED_VIDEO_TYPES = List.of("video/mp4", "video/quicktime", "video/webm");
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif",
            "video/mp4", ".mp4",
            "video/quicktime", ".mov",
            "video/webm", ".webm"
    );
    private static final long PRESIGN_UPLOAD_EXPIRY_SECONDS = 15L * 60;

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

        UserEntity user = userRepository.findActiveByUsername(username)
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
    public CreatePostUploadUrlResponse createUploadUrl(UUID userId, CreatePostUploadUrlRequest request) {
        log.info("NEW.POST.UPLOAD.URL: {}", userId);

        UserEntity user = userRepository.findActiveById(userId)
                .orElseThrow(() -> {
                    log.error("Active user not found {}", userId);
                    return new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
                });

        String contentType = request.getContentType();
        if (contentType == null || !isAllowedContentType(contentType)) {
            log.error("Unsupported media type: {}", contentType);
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        String extension = CONTENT_TYPE_EXTENSIONS.get(contentType);
        if (extension == null) {
            // derive from image sub type, e.g. image/avif -> .avif
            extension = "." + contentType.substring(contentType.indexOf('/') + 1);
        }

        // format it: username/POST_FEED/{uuid}{ext}
        String objectKey = String.format("%s/%s/%s%s",
                user.getUsername(),
                ObjectStorageTypeGroup.POST_FEED,
                UUID.randomUUID(),
                extension);

        try {
            String presignedUrl = fileStorageService.getPresignUploadUrl(objectKey, contentType);
            return CreatePostUploadUrlResponse.builder()
                    .presignedUrl(presignedUrl)
                    .objectKey(objectKey)
                    .expiresInSeconds(PRESIGN_UPLOAD_EXPIRY_SECONDS)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create presign upload url for user {}: {}", userId, e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CreatePostResponse addNewPost(UUID userId, AddPostRequest request) {
        log.info("NEW.FEED.POST: {}", userId);

        UserEntity user = userRepository.findActiveById(userId)
                .orElseThrow(() -> {
                    log.error("Active user not found {}", userId);
                    return new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
                });

        String objectKey = request.getObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            log.error("Object key is empty");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        // object must belong to this user and to POST_FEED group
        String expectedPrefix = user.getUsername() + "/" + ObjectStorageTypeGroup.POST_FEED + "/";
        if (!objectKey.startsWith(expectedPrefix)) {
            log.error("Object key does not belong to user: objectKey={}, userId={}", objectKey, userId);
            throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        // validate the uploaded object. The media type is derived from the object
        // key extension (the backend generated it from the requested content type),
        // while statObject is best-effort for the size check: a transient storage
        // failure (e.g. proxy/HEAD hiccup) must not fail the whole post creation.
        MediaType mediaType = resolveMediaTypeFromKey(objectKey);
        if (mediaType == null) {
            log.error("Unsupported media extension: objectKey={}", objectKey);
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        ObjectStat objectStat = null;
        try {
            objectStat = fileStorageService.statObject(objectKey);
        } catch (BusinessException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getHttpStatus())) {
                throw e;
            }
            log.warn(":: POST.STAT.SKIPPED: objectKey={} reason={}", objectKey, e.getMessage());
        } catch (Exception e) {
            log.warn(":: POST.STAT.SKIPPED: objectKey={} reason={}", objectKey, e.getMessage());
        }

        // Size validation is a hard business rule and must never be swallowed by
        // the best-effort stat handling above.
        if (objectStat != null) {
            long size = objectStat.getSize();
            if (mediaType == MediaType.IMAGE && size > MAX_IMAGE_SIZE) {
                log.error("Image too large: {} bytes", size);
                throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
            }
            if (mediaType == MediaType.VIDEO && size > MAX_VIDEO_SIZE) {
                log.error("Video too large: {} bytes", size);
                throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
            }
        }

        Map<String, Object> emailProps = new HashMap<>();
        emailProps.put("username", user.getUsername());
        emailProps.put("isSuccess", true);
        emailProps.put("postTitle", request.getCaption());

        PostEntity postEntity = postCrudRepository.save(
                PostEntity.builder()
                        .caption(request.getCaption())
                        .createdAt(LocalDateTime.now())
                        .mediaUrl(objectKey)
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

        // The post is saved with a null thumbnail. The media worker generates one
        // asynchronously and reports back via VideoThumbnailResultConsumer.
        if (postEntity.getMediaType() == MediaType.VIDEO) {
            postEventPublisher.publishVideoThumbnailRequested(
                    VideoThumbnailRequestedEvent.of(postEntity.getId(), postEntity.getMediaUrl()));
        }

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

    private boolean isAllowedContentType(String contentType) {
        return contentType.startsWith("image/") || ALLOWED_VIDEO_TYPES.contains(contentType);
    }

    private MediaType resolveMediaTypeFromKey(String objectKey) {
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            return MediaType.IMAGE;
        }
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm")) {
            return MediaType.VIDEO;
        }
        return null;
    }
}
