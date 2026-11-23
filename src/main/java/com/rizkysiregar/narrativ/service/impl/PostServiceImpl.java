package com.rizkysiregar.narrativ.service.impl;


import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.constant.ObjectStorageTypeGroup;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.messaging.event.PostCreatedEvent;
import com.rizkysiregar.narrativ.messaging.event.VideoThumbnailRequestedEvent;
import com.rizkysiregar.narrativ.messaging.publisher.PostEventPublisher;
import com.rizkysiregar.narrativ.model.dto.PostCountDTO;
import com.rizkysiregar.narrativ.model.dto.PostSearchHit;
import com.rizkysiregar.narrativ.model.response.CreatePostResponse;
import com.rizkysiregar.narrativ.model.response.CreatePostUploadUrlResponse;
import com.rizkysiregar.narrativ.model.response.PostFeedResponse;
import com.rizkysiregar.narrativ.model.response.PostMediaResponse;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.PostMediaEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.request.AddPostRequest;
import com.rizkysiregar.narrativ.model.request.CreatePostUploadUrlRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.model.enums.MediaType;
import com.rizkysiregar.narrativ.model.enums.ModerationStatus;
import com.rizkysiregar.narrativ.repository.*;
import com.rizkysiregar.narrativ.service.FileStorageService;
import com.rizkysiregar.narrativ.service.PostService;
import com.rizkysiregar.narrativ.service.MediaCleanupService;
import com.rizkysiregar.narrativ.service.ProfileImageUrlResolver;
import com.rizkysiregar.narrativ.service.UploadedObjectStats;
import com.rizkysiregar.narrativ.util.AfterCommit;
import com.rizkysiregar.narrativ.service.feed.RankedFeedService;
import com.rizkysiregar.narrativ.util.FeedCursor;
import com.rizkysiregar.narrativ.util.RankedCursor;
import com.rizkysiregar.narrativ.util.Operation;
import com.rizkysiregar.narrativ.util.Paging;
import com.rizkysiregar.narrativ.util.SearchTerm;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostPaginationRepository postPaginationRepository;
    private final UserBlockRepository userBlockRepository;
    private final MediaCleanupService mediaCleanupService;
    private final PostCrudRepository postCrudRepository;
    private final PostMediaRepository postMediaRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PostEventPublisher postEventPublisher;
    private final ProfileImageUrlResolver profileImageUrlResolver;
    private final RankedFeedService rankedFeedService;

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

    /**
     * Explore is ranked: the recent posts by engagement and age, new faces before people the viewer follows and
     * their own posts ({@link RankedFeedService}), then the older ones by date. It is only empty when there is no
     * post to show at all. A numbered page is cut from a fresh ranking.
     */
    @Override
    public PaginatedResponse<PostFeedResponse> getExplorePosts(int page, int size, String cursor, UUID userId, boolean mediaOnly) {
        log.debug("FEED.EXPLORE.FETCH page={} size={} cursor={} userId={} mediaOnly={}", page, size, cursor != null, userId, mediaOnly);
        if (cursor != null) {
            Paging.of(0, size);
            Optional<RankedCursor> ranked = RankedCursor.tryDecode(cursor);
            if (ranked.isPresent()) {
                // an expired ranking goes on by date below it, which keeps every post of it out of the next pages
                return rankedFeedService.resume(ranked.get(), userId, size)
                        .map(slice -> exploreFrom(slice, size, userId, mediaOnly))
                        .orElseGet(() -> exploreByDate(ranked.get().afterRanking(), List.of(), size, userId, mediaOnly));
            }
            return exploreByDate(FeedCursor.decode(cursor), List.of(), size, userId, mediaOnly);
        }
        Pageable pageable = Paging.of(page, size);
        return rankedFeedService.start(RankedFeedService.Feed.EXPLORE, userId, mediaOnly, (int) pageable.getOffset(), size)
                .map(slice -> exploreFrom(slice, size, userId, mediaOnly))
                // no Redis, no ranking: explore by date, as it was before
                .orElseGet(() -> exploreByDate(new FeedCursor(LocalDateTime.now().plusDays(1), new UUID(-1L, -1L)),
                        List.of(), size, userId, mediaOnly));
    }

    private PaginatedResponse<PostFeedResponse> exploreFrom(RankedFeedService.Slice slice, int size, UUID viewerId,
                                                            boolean mediaOnly) {
        if (slice.next() != null) {
            return cursorPage(slice.posts(), Set.of(), size, slice.next().encode(), viewerId);
        }
        // the ranking ran out within this page: the older posts by date fill it up
        return exploreByDate(RankedCursor.below(slice.windowStart()), slice.posts(), size, viewerId, mediaOnly);
    }

    /** A page of explore by date after {@code after}, appended to what the page already holds. */
    private PaginatedResponse<PostFeedResponse> exploreByDate(FeedCursor after, List<PostEntity> already, int size,
                                                              UUID viewerId, boolean mediaOnly) {
        int missing = size - already.size();
        // one row more than missing tells whether another page follows
        List<PostEntity> rows = postPaginationRepository.findExplorePostsAfter(
                mediaOnly, viewerId, after.createdAt(), after.id(), PageRequest.of(0, missing + 1));
        boolean hasMore = rows.size() > missing;
        List<PostEntity> byDate = hasMore ? rows.subList(0, missing) : rows;
        List<PostEntity> posts = new ArrayList<>(already);
        posts.addAll(byDate);
        String next = !hasMore ? null : byDate.isEmpty() ? after.encode() : cursorOf(byDate.getLast());
        return cursorPage(posts, Set.of(), size, next, viewerId);
    }

    /**
     * The home feed: the viewer's own posts and those of the people they follow, by date. Once they run out the
     * feed goes on with suggestions (ranked posts of people they do not follow, marked {@code suggested}), so a new
     * user who follows nobody yet does not land on an empty screen. Only page 0 and cursor pages get them: a client paging by
     * number would otherwise get the same suggestions again on every page after the last.
     */
    @Override
    public PaginatedResponse<PostFeedResponse> getFollowingPosts(int page, int size, String cursor, UUID viewerId) {
        if (cursor != null) {
            Paging.of(0, size);
            Optional<RankedCursor> suggestions = RankedCursor.tryDecode(cursor);
            if (suggestions.isPresent()) {
                // the suggestions end with their ranking, the home feed has no date order to go on with
                return rankedFeedService.resume(suggestions.get(), viewerId, size)
                        .map(slice -> withSuggestions(List.of(), slice, size, viewerId))
                        .orElseGet(() -> cursorPage(List.of(), Set.of(), size, null, viewerId));
            }
            FeedCursor after = FeedCursor.decode(cursor);
            List<PostEntity> rows = postPaginationRepository.findFollowingPostsAfter(
                    viewerId, after.createdAt(), after.id(), PageRequest.of(0, size + 1));
            if (rows.size() > size) {
                List<PostEntity> posts = rows.subList(0, size);
                return cursorPage(posts, Set.of(), size, cursorOf(posts.getLast()), viewerId);
            }
            return startSuggestions(rows, size, viewerId);
        }
        Page<PostEntity> postPage = postPaginationRepository.findFollowingPosts(Paging.of(page, size), viewerId);
        if (page > 0 || !postPage.isLast()) {
            return buildPaginatedResponse(postPage, viewerId);
        }
        PaginatedResponse<PostFeedResponse> firstPage = startSuggestions(postPage.getContent(), size, viewerId);
        return new PaginatedResponse<>(firstPage.getContent(), postPage.getNumber(), postPage.getSize(),
                postPage.getTotalElements(), postPage.getTotalPages(), firstPage.isLast(), firstPage.getNextCursor());
    }

    // the last posts of the home feed, and suggestions in the room left on the page
    private PaginatedResponse<PostFeedResponse> startSuggestions(List<PostEntity> homePosts, int size, UUID viewerId) {
        return rankedFeedService.start(RankedFeedService.Feed.SUGGESTIONS, viewerId, false, 0, size - homePosts.size())
                .map(slice -> withSuggestions(homePosts, slice, size, viewerId))
                .orElseGet(() -> cursorPage(homePosts, Set.of(), size, null, viewerId));
    }

    private PaginatedResponse<PostFeedResponse> withSuggestions(List<PostEntity> homePosts, RankedFeedService.Slice slice,
                                                                int size, UUID viewerId) {
        List<PostEntity> posts = new ArrayList<>(homePosts);
        posts.addAll(slice.posts());
        Set<UUID> suggested = slice.posts().stream().map(PostEntity::getId).collect(Collectors.toSet());
        return cursorPage(posts, suggested, size, slice.next() == null ? null : slice.next().encode(), viewerId);
    }

    @Override
    public PaginatedResponse<PostFeedResponse> getAllPostByUserId(int page, int size, String cursor, String username, UUID viewerId, boolean mediaOnly) {
        log.debug("FEED.USER.FETCH page={} size={} cursor={} username={} mediaOnly={}", page, size, cursor != null, username, mediaOnly);

        // checked first, so a bad request never costs a lookup
        FeedCursor after = cursor == null ? null : FeedCursor.decode(cursor);
        Pageable pageable = after == null ? Paging.of(page, size) : Paging.of(0, size);

        UserEntity user = userRepository.findActiveByUsername(username)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (userBlockRepository.existsBetween(viewerId, user.getId())) {
            throw new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        if (after != null) {
            return afterCursor(size, viewerId, limit -> postPaginationRepository.findAllActivePostsByUserIdAfter(
                    user.getId(), mediaOnly, viewerId, after.createdAt(), after.id(), limit));
        }
        Page<PostEntity> postPage = postPaginationRepository.findAllActivePostsByUserId(pageable, user.getId(), mediaOnly, viewerId);
        return buildPaginatedResponse(postPage, viewerId);
    }

    /**
     * One page after a cursor. One row more than asked is read, which tells whether another page follows without a
     * count query.
     */
    private PaginatedResponse<PostFeedResponse> afterCursor(int size, UUID viewerId,
                                                            Function<Pageable, List<PostEntity>> query) {
        Paging.of(0, size);
        List<PostEntity> rows = query.apply(PageRequest.of(0, size + 1));
        boolean hasMore = rows.size() > size;
        List<PostEntity> posts = hasMore ? rows.subList(0, size) : rows;
        return cursorPage(posts, Set.of(), size, hasMore ? cursorOf(posts.getLast()) : null, viewerId);
    }

    // a page without totals (-1): it is the last one when no cursor follows
    @Override
    public PaginatedResponse<PostFeedResponse> searchPosts(String query, int size, String cursor, UUID viewerId) {
        SearchTerm term = SearchTerm.of(query);
        Paging.of(0, size);
        FeedCursor after = cursor == null
                ? new FeedCursor(LocalDateTime.now().plusDays(1), new UUID(-1L, -1L))
                : FeedCursor.decode(cursor);
        // the length, not the words: what people search for is theirs
        log.debug("POST.SEARCH termLength={} size={} cursor={} viewerId={}", term.value().length(), size, cursor != null, viewerId);

        String tsQuery = term.prefixTsQuery();
        if (tsQuery.isEmpty()) {
            // only punctuation: no word a caption could hold
            return cursorPage(List.of(), Set.of(), size, null, viewerId);
        }
        // one row more than the page tells whether another page follows
        List<PostSearchHit> hits = postPaginationRepository.searchPosts(tsQuery, viewerId, after.createdAt(), after.id(), size + 1);
        boolean hasMore = hits.size() > size;
        List<PostSearchHit> page = hasMore ? hits.subList(0, size) : hits;
        if (page.isEmpty()) {
            return cursorPage(List.of(), Set.of(), size, null, viewerId);
        }
        // loaded with their authors in one go; a post deleted or hidden since the search is left out
        Map<UUID, PostEntity> byId = postPaginationRepository
                .findRankedVisibleByIds(page.stream().map(PostSearchHit::getId).toList(), viewerId).stream()
                .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
        List<PostEntity> posts = page.stream().map(hit -> byId.get(hit.getId())).filter(Objects::nonNull).toList();
        // the cursor is the last hit, found or not, so the next page never repeats this one
        PostSearchHit last = page.getLast();
        String next = hasMore ? new FeedCursor(last.getCreatedAt(), last.getId()).encode() : null;
        return cursorPage(posts, Set.of(), size, next, viewerId);
    }

    private PaginatedResponse<PostFeedResponse> cursorPage(List<PostEntity> posts, Set<UUID> suggestedIds, int size,
                                                           String nextCursor, UUID viewerId) {
        List<PostFeedResponse> content = toFeedResponses(posts, viewerId);
        content.stream().filter(post -> suggestedIds.contains(post.getId())).forEach(post -> post.setSuggested(true));
        return new PaginatedResponse<>(content, -1, size, -1, -1, nextCursor == null, nextCursor);
    }

    private static String cursorOf(PostEntity post) {
        return new FeedCursor(post.getCreatedAt(), post.getId()).encode();
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

    // a numbered page also hands out the cursor after its last post, so a client can switch to cursors from page 0
    private PaginatedResponse<PostFeedResponse> buildPaginatedResponse(Page<PostEntity> postPage, UUID statusUserId) {
        return new PaginatedResponse<>(
                toFeedResponses(postPage.getContent(), statusUserId),
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages(),
                postPage.isLast(),
                postPage.isLast() || postPage.isEmpty() ? null : cursorOf(postPage.getContent().getLast()));
    }

    @Override
    public PostFeedResponse getPostById(UUID postId, UUID viewerId) {
        log.debug("POST.FETCH postId={} viewerId={}", postId, viewerId);
        // deleted, banned author and blocked all answer like a post that does not exist
        PostEntity post = postPaginationRepository.findVisibleById(postId, viewerId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));
        return toFeedResponses(List.of(post), viewerId).getFirst();
    }

    private List<PostFeedResponse> toFeedResponses(List<PostEntity> posts, UUID statusUserId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(PostEntity::getId).toList();
        Map<UUID, Long> likeCounts = likeRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostCountDTO::postId, PostCountDTO::count));
        Map<UUID, Long> commentCounts = commentRepository.countByPostIds(postIds, statusUserId).stream()
                .collect(Collectors.toMap(PostCountDTO::postId, PostCountDTO::count));
        Set<UUID> likedPostIds = new HashSet<>(likeRepository.findLikedPostIds(statusUserId, postIds));
        Map<UUID, List<PostMediaResponse>> mediaByPost = postMediaRepository
                .findByPostIdInOrderByPostIdAscPositionAsc(postIds).stream()
                .collect(Collectors.groupingBy(PostMediaEntity::getPostId,
                        Collectors.mapping(m -> new PostMediaResponse(
                                m.getId(), m.getMediaType(), m.getMediaKey(), m.getThumbnailKey()),
                                Collectors.toList())));
        // one presign per author, a page often holds several posts of the same person
        Map<UUID, String> profileImageUrls = new HashMap<>();

        return posts.stream().map(post -> {
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
            response.setUserId(post.getUser().getId());
            response.setProfileImageUrl(profileImageUrls.computeIfAbsent(post.getUser().getId(),
                    id -> profileImageUrlResolver.resolve(post.getUser().getProfileImage())));
            applyModeration(response, post, statusUserId);
            response.setMedia(response.getMedia().stream().map(this::presign).toList());
            return response;
        }).toList();
    }

    // the media still carries object keys here; a failed presign keeps the key so one bad object cannot break the feed
    private PostMediaResponse presign(PostMediaResponse media) {
        try {
            String thumbnail = media.thumbnailUrl() == null ? null : fileStorageService.getPresignUrl(media.thumbnailUrl());
            return new PostMediaResponse(media.id(), media.type(), fileStorageService.getPresignUrl(media.url()), thumbnail);
        } catch (Exception e) {
            log.error("POST.MEDIA.PRESIGN.FAILED objectKey={} reason={}", media.url(), e.getMessage());
            return media;
        }
    }

    @Override
    public CreatePostUploadUrlResponse createUploadUrl(UUID userId, CreatePostUploadUrlRequest request) {
        log.debug("POST.UPLOAD.URL.REQUESTED userId={}", userId);

        UserEntity user = userRepository.findActiveById(userId)
                .orElseThrow(() -> {
                    log.warn("POST.UPLOAD.URL.REJECTED reason=user_not_active userId={}", userId);
                    return new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
                });

        String contentType = request.getContentType();
        if (contentType == null || !isAllowedContentType(contentType)) {
            log.warn("POST.UPLOAD.URL.REJECTED reason=unsupported_content_type contentType={}", contentType);
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        String extension = CONTENT_TYPE_EXTENSIONS.get(contentType);
        if (extension == null) {
            // derive from image sub type, e.g. image/avif -> .avif
            extension = "." + contentType.substring(contentType.indexOf('/') + 1);
        }

        // format it: userId/POST_FEED/{uuid}{ext}. The id, not the username: a username can be changed and then
        // taken by somebody else, who would otherwise own the folder of the earlier holder
        String objectKey = String.format("%s/%s/%s%s",
                user.getId(),
                ObjectStorageTypeGroup.POST_FEED,
                UUID.randomUUID(),
                extension);

        try {
            String presignedUrl = fileStorageService.getPresignUploadUrl(objectKey, contentType);
            long maxBytes = ALLOWED_VIDEO_TYPES.contains(contentType) ? MAX_VIDEO_SIZE : MAX_IMAGE_SIZE;
            return CreatePostUploadUrlResponse.builder()
                    .presignedUrl(presignedUrl)
                    .uploadForm(fileStorageService.getPresignUploadForm(objectKey, contentType, maxBytes))
                    .objectKey(objectKey)
                    .expiresInSeconds(PRESIGN_UPLOAD_EXPIRY_SECONDS)
                    .build();
        } catch (Exception e) {
            log.error("POST.UPLOAD.URL.FAILED userId={} reason={}", userId, e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public CreatePostResponse addNewPost(UUID userId, AddPostRequest request) {
        log.debug("POST.CREATE.START userId={}", userId);

        UserEntity user = userRepository.findActiveById(userId)
                .orElseThrow(() -> {
                    log.warn("POST.CREATE.REJECTED reason=user_not_active userId={}", userId);
                    return new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
                });

        String caption = request.getCaption() == null || request.getCaption().isBlank() ? null : request.getCaption();
        if (caption != null && caption.length() > MAX_CAPTION_LENGTH) {
            log.warn("POST.CREATE.REJECTED reason=caption_too_long length={}", caption.length());
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        List<String> mediaKeys = request.getMediaKeys() == null ? List.of() : request.getMediaKeys();
        // a post needs a caption or at least one media; the keys must be distinct
        if ((caption == null && mediaKeys.isEmpty())
                || mediaKeys.size() > MAX_MEDIA_PER_POST
                || new HashSet<>(mediaKeys).size() != mediaKeys.size()) {
            log.warn("POST.CREATE.REJECTED reason=invalid_content hasCaption={} mediaCount={}", caption != null, mediaKeys.size());
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
        log.info("POST.CREATED postId={} userId={} mediaCount={}", postEntity.getId(), userId, savedItems.size());

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
            log.warn("POST.MEDIA.REJECTED reason=empty_key");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        // object must belong to this user and to POST_FEED group
        String expectedPrefix = user.getId() + "/" + ObjectStorageTypeGroup.POST_FEED + "/";
        if (!objectKey.startsWith(expectedPrefix)) {
            log.warn("POST.MEDIA.REJECTED reason=not_owner objectKey={} userId={}", objectKey, user.getId());
            throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        // The media type is derived from the object key extension (the backend generated it from the requested
        // content type, which the presigned upload enforces). Size is a hard rule, so an object whose size storage
        // cannot tell right now is refused rather than accepted unchecked.
        MediaType mediaType = resolveMediaTypeFromKey(objectKey);
        if (mediaType == null) {
            log.warn("POST.MEDIA.REJECTED reason=unsupported_extension objectKey={}", objectKey);
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        long size = UploadedObjectStats.statOrRefuse(fileStorageService, objectKey).getSize();
        if (mediaType == MediaType.IMAGE && size > MAX_IMAGE_SIZE) {
            log.warn("POST.MEDIA.REJECTED reason=image_too_large objectKey={} bytes={}", objectKey, size);
            throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
        }
        if (mediaType == MediaType.VIDEO && size > MAX_VIDEO_SIZE) {
            log.warn("POST.MEDIA.REJECTED reason=video_too_large objectKey={} bytes={}", objectKey, size);
            throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
        }
        return mediaType;
    }

    @Override
    @Transactional
    public Operation deletePost(UUID postId, UUID userId) {
        log.info("POST.DELETED postId={} userId={}", postId, userId);

        PostEntity post = postCrudRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        post.setDeleted(true);
        postCrudRepository.save(post);

        // the rows go with the post: a file that cannot be deleted right now is then an orphan for the cleanup job,
        // not a file that stays linked to a deleted post forever
        List<String> objectKeys = new ArrayList<>();
        for (PostMediaEntity item : postMediaRepository.findByPostIdOrderByPositionAsc(post.getId())) {
            objectKeys.add(item.getMediaKey());
            if (item.getThumbnailKey() != null) {
                objectKeys.add(item.getThumbnailKey());
            }
        }
        postMediaRepository.deleteAllByPostId(post.getId());
        if (!objectKeys.isEmpty()) {
            AfterCommit.run("delete media of post " + post.getId(), () -> mediaCleanupService.deleteObjects(objectKeys));
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
