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
import com.rizkysiregar.feedzmore.model.response.PostMediaResponse;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.PostMediaEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.request.CreatePostUploadUrlRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import com.rizkysiregar.feedzmore.repository.*;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import com.rizkysiregar.feedzmore.service.PostService;
import com.rizkysiregar.feedzmore.util.AfterCommit;
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
import java.util.ArrayList;
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
    private final PostMediaRepository postMediaRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PostEventPublisher postEventPublisher;

    private static final int MAX_MEDIA_PER_POST = 5;
    private static final int MAX_CAPTION_LENGTH = 500;
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
    public PaginatedResponse<PostFeedResponse> getExplorePosts(int page, int size, UUID userId, boolean mediaOnly) {
        log.info("FETCH.EXPLORE.FEED: {}, size= {} ,userId={}, mediaOnly={}", page, size, userId, mediaOnly);
        Pageable pageable = PageRequest.of(page, size);
        Page<PostEntity> postPage = postPaginationRepository.findAllActivePosts(pageable, mediaOnly);
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
    public PaginatedResponse<PostFeedResponse> getAllPostByUserId(int page, int size, String username, UUID viewerId, boolean mediaOnly) {
        log.info("FETCH.FEED.BY.USERNAME =  {}, size= {} , username= {}, mediaOnly= {}", page, size, username, mediaOnly);

        UserEntity user = userRepository.findActiveByUsername(username)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size);
        Page<PostEntity> postPage = postPaginationRepository.findAllActivePostsByUserId(pageable, user.getId(), mediaOnly);
        return buildPaginatedResponse(postPage, viewerId);
    }

    /**
     * A taken down post stays in the list with its status so the client can filter on it. Its author also gets
     * the reason and keeps seeing his own caption and media; everybody else gets neither, so the content
     * cannot be read through the API.
     */
    private void applyModeration(PostFeedResponse response, PostEntity post, UUID viewerId) {
        response.setModerationStatus(post.getModerationStatus());
        if (post.getModerationStatus() != ModerationStatus.TAKEN_DOWN) {
            return;
        }
        response.setTakenDownAt(post.getModeratedAt());
        if (viewerId != null && viewerId.equals(post.getUser().getId())) {
            response.setTakedownReason(post.getModerationReason());
        } else {
            response.setCaption(null);
            response.setMedia(List.of());
        }
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
            Map<UUID, List<PostMediaResponse>> mediaByPost = postMediaRepository
                    .findByPostIdInOrderByPostIdAscPositionAsc(postIds).stream()
                    .collect(Collectors.groupingBy(PostMediaEntity::getPostId,
                            Collectors.mapping(m -> new PostMediaResponse(
                                    m.getId(), m.getMediaType(), m.getMediaKey(), m.getThumbnailKey()),
                                    Collectors.toList())));

            content = posts.stream().map(post -> {
                PostFeedResponse response = new PostFeedResponse(
                        post.getId(),
                        post.getUser().getUsername(),
                        post.getCaption(),
                        mediaByPost.getOrDefault(post.getId(), List.of()),
                        post.getCreatedAt(),
                        likeCounts.getOrDefault(post.getId(), 0L),
                        commentCounts.getOrDefault(post.getId(), 0L),
                        likedPostIds.contains(post.getId())
                );
                applyModeration(response, post, statusUserId);
                response.setMedia(response.getMedia().stream().map(this::presign).toList());
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

    // the media still carries object keys here; a failed presign keeps the key so one bad object cannot break the feed
    private PostMediaResponse presign(PostMediaResponse media) {
        try {
            String thumbnail = media.thumbnailUrl() == null ? null : fileStorageService.getPresignUrl(media.thumbnailUrl());
            return new PostMediaResponse(media.id(), media.type(), fileStorageService.getPresignUrl(media.url()), thumbnail);
        } catch (Exception e) {
            log.error("## cannot get presign url for objectName= {}, because of= {}", media.url(), e.getMessage());
            return media;
        }
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
    @Transactional
    public CreatePostResponse addNewPost(UUID userId, AddPostRequest request) {
        log.info("NEW.FEED.POST: {}", userId);

        UserEntity user = userRepository.findActiveById(userId)
                .orElseThrow(() -> {
                    log.error("Active user not found {}", userId);
                    return new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
                });

        String caption = request.getCaption() == null || request.getCaption().isBlank() ? null : request.getCaption();
        if (caption != null && caption.length() > MAX_CAPTION_LENGTH) {
            log.error("Caption too long: {} chars", caption.length());
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        List<String> mediaKeys = request.getMediaKeys() == null ? List.of() : request.getMediaKeys();
        // a post needs a caption or at least one media; the keys must be distinct
        if ((caption == null && mediaKeys.isEmpty())
                || mediaKeys.size() > MAX_MEDIA_PER_POST
                || new HashSet<>(mediaKeys).size() != mediaKeys.size()) {
            log.error("Invalid post content: caption={}, mediaCount={}", caption != null, mediaKeys.size());
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        List<MediaType> mediaTypes = new ArrayList<>();
        for (String objectKey : mediaKeys) {
            mediaTypes.add(validateMedia(user, objectKey));
        }

        Map<String, Object> emailProps = new HashMap<>();
        emailProps.put("username", user.getUsername());
        emailProps.put("isSuccess", true);
        // the email shows this as the post title; a media-only post has no caption
        emailProps.put("postTitle", caption != null ? caption : "Untitled post (" + mediaKeys.size() + " media)");

        PostEntity postEntity = postCrudRepository.save(
                PostEntity.builder()
                        .caption(caption)
                        .createdAt(LocalDateTime.now())
                        .user(user)
                        .build()
        );

        LocalDateTime now = LocalDateTime.now();
        List<PostMediaEntity> items = new ArrayList<>();
        for (int i = 0; i < mediaKeys.size(); i++) {
            items.add(PostMediaEntity.builder()
                    .postId(postEntity.getId())
                    .position(i)
                    .mediaKey(mediaKeys.get(i))
                    .mediaType(mediaTypes.get(i))
                    .createdAt(now)
                    .build());
        }
        List<PostMediaEntity> savedItems = items.isEmpty() ? List.of() : postMediaRepository.saveAll(items);

        // event to publish
        PostCreatedEvent event = PostCreatedEvent.builder()
                .postId(postEntity.getId())
                .authorId(user.getId())
                .username(postEntity.getUser().getUsername())
                .caption(postEntity.getCaption())
                .mediaCount(savedItems.size())
                .createdAt(postEntity.getCreatedAt())
                .email(user.getEmail())
                .emailProps(emailProps)
                .build();

        // Only once the post and its media are committed: the consumers and the media worker look them up by id.
        AfterCommit.run("publish events of post " + postEntity.getId(), () -> {
            // broadcast post created event, currently the consumer are: notification and email
            postEventPublisher.publishPostCreatedEvent(event);

            // The videos are saved with a null thumbnail. The media worker generates one asynchronously and
            // reports back via VideoThumbnailResultConsumer.
            for (PostMediaEntity item : savedItems) {
                if (item.getMediaType() == MediaType.VIDEO) {
                    postEventPublisher.publishVideoThumbnailRequested(
                            VideoThumbnailRequestedEvent.of(postEntity.getId(), item.getId(), item.getMediaKey()));
                }
            }
        });

        return CreatePostResponse.builder()
                .status(GlobalMessage.CREATED.getMessage())
                .build();
    }

    /** Checks one uploaded object belongs to the user and fits the size limit; returns its media type. */
    private MediaType validateMedia(UserEntity user, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            log.error("Object key is empty");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        // object must belong to this user and to POST_FEED group
        String expectedPrefix = user.getUsername() + "/" + ObjectStorageTypeGroup.POST_FEED + "/";
        if (!objectKey.startsWith(expectedPrefix)) {
            log.error("Object key does not belong to user: objectKey={}, userId={}", objectKey, user.getId());
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
        return mediaType;
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

        for (PostMediaEntity item : postMediaRepository.findByPostIdOrderByPositionAsc(post.getId())) {
            fileStorageService.deleteObject(item.getMediaKey());
            if (item.getThumbnailKey() != null) {
                fileStorageService.deleteObject(item.getThumbnailKey());
            }
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
