package com.rizkysiregar.narrativ.service;


import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.messaging.event.PostCreatedEvent;
import com.rizkysiregar.narrativ.messaging.event.VideoThumbnailRequestedEvent;
import com.rizkysiregar.narrativ.model.enums.MediaType;
import com.rizkysiregar.narrativ.messaging.publisher.PostEventPublisher;
import com.rizkysiregar.narrativ.model.dto.ObjectStat;
import com.rizkysiregar.narrativ.model.response.CreatePostResponse;
import com.rizkysiregar.narrativ.model.response.CreatePostUploadUrlResponse;
import com.rizkysiregar.narrativ.model.response.PostFeedResponse;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.PostMediaEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.request.AddPostRequest;
import com.rizkysiregar.narrativ.model.request.CreatePostUploadUrlRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.repository.*;
import com.rizkysiregar.narrativ.service.impl.PostServiceImpl;
import com.rizkysiregar.narrativ.service.feed.RankedFeedService;
import com.rizkysiregar.narrativ.util.FeedCursor;
import com.rizkysiregar.narrativ.util.RankedCursor;
import com.rizkysiregar.narrativ.util.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.rizkysiregar.narrativ.model.enums.ModerationStatus;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PostServiceTest {

    @Mock
    private PostPaginationRepository postPaginationRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private MediaCleanupService mediaCleanupService;
    @Mock
    private PostCrudRepository postCrudRepository;
    @Mock
    private PostMediaRepository postMediaRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private PostEventPublisher postEventPublisher;
    @Mock
    private ProfileImageUrlResolver profileImageUrlResolver;
    @Mock
    private RankedFeedService rankedFeedService;

    @InjectMocks
    private PostServiceImpl postService;

    private static PostMediaEntity media(UUID postId, int position, String key, MediaType type, String thumbnailKey) {
        return PostMediaEntity.builder().id(UUID.randomUUID()).postId(postId).position(position)
                .mediaKey(key).mediaType(type).thumbnailKey(thumbnailKey).createdAt(LocalDateTime.now()).build();
    }

    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2026, 11, 11, 10, 0);

    // explore serves these posts from a ranking that holds more after them
    private void rankedPage(PostEntity... posts) {
        when(rankedFeedService.start(any(), anyBoolean(), anyInt(), anyInt())).thenReturn(Optional.of(
                new RankedFeedService.Slice(List.of(posts), new RankedCursor(UUID.randomUUID(), posts.length, WINDOW_START), WINDOW_START)));
    }

    private static PostEntity post(UserEntity author, LocalDateTime createdAt) {
        return PostEntity.builder().id(UUID.randomUUID()).user(author).caption("c").createdAt(createdAt).build();
    }

    private static UserEntity someone() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("u" + user.getId().toString().substring(0, 8));
        return user;
    }

    private static AddPostRequest request(String caption, String... mediaKeys) {
        return AddPostRequest.builder().caption(caption).mediaKeys(List.of(mediaKeys)).build();
    }

    @Test
    void testExplorePosts_ReturnsPaginatedResponse() {
        // Arrange
        int page = 0;
        int size = 2;
        UUID userId = UUID.randomUUID();
        UserEntity alice = new UserEntity();
        alice.setUsername("alice");
        UserEntity bob = new UserEntity();
        bob.setUsername("bob");
        PostEntity p1 = PostEntity.builder().id(UUID.randomUUID()).user(alice).caption("hello")
                .createdAt(LocalDateTime.now()).build();
        PostEntity p2 = PostEntity.builder().id(UUID.randomUUID()).user(bob).caption("world")
                .createdAt(LocalDateTime.now().minusMinutes(1)).build();
        List<PostEntity> posts = List.of(p1, p2);
        RankedCursor next = new RankedCursor(UUID.randomUUID(), 2, WINDOW_START);
        when(rankedFeedService.start(userId, false, 0, size))
                .thenReturn(Optional.of(new RankedFeedService.Slice(posts, next, WINDOW_START)));

        when(postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(any())).thenReturn(List.of(
                media(p1.getId(), 0, "img1.jpg", MediaType.IMAGE, null),
                media(p2.getId(), 0, "img2.jpg", MediaType.IMAGE, null)));

        try {
            when(fileStorageService.getPresignUrl("img1.jpg")).thenReturn("img1.jpg");
            when(fileStorageService.getPresignUrl("img2.jpg")).thenReturn("img2.jpg");
        } catch (Exception e) {
            // do nothing
        }

        // Act
        PaginatedResponse<PostFeedResponse> response = postService.getExplorePosts(page, size, userId, false);

        // Assert
        assertNotNull(response);
        assertEquals(size, response.getPageSize());
        assertEquals(posts.size(), response.getContent().size());
        // a ranked page has no totals; the next one is cut from the same ranking
        assertEquals(-1, response.getTotalElements());
        assertFalse(response.isLast());
        assertEquals(next.encode(), response.getNextCursor());
        // image url should be kept as is
        assertEquals("img1.jpg", response.getContent().get(0).getMedia().get(0).url());
        assertEquals("img2.jpg", response.getContent().get(1).getMedia().get(0).url());

        verify(postPaginationRepository, never()).findExplorePostsAfter(anyBoolean(), any(), any(), any(), any());
        verify(postPaginationRepository, never()).findFollowingPosts(any(Pageable.class), any(UUID.class));
    }

    @Test
    void testFollowingPosts_EmptyWhenNoFollowedPosts() {
        UUID viewerId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);
        when(postPaginationRepository.findFollowingPosts(pageable, viewerId))
                .thenReturn(Page.empty(pageable));

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(0, 10, viewerId);

        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());
        verifyNoInteractions(likeRepository, commentRepository, fileStorageService);
    }

    @Test
    void testFollowingPosts_UsesViewerLikeStatus() throws Exception {
        UUID viewerId = UUID.randomUUID();
        UserEntity author = new UserEntity();
        author.setUsername("followed");
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(author)
                .caption("hello").createdAt(LocalDateTime.now()).build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(postPaginationRepository.findFollowingPosts(pageable, viewerId))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(any()))
                .thenReturn(List.of(media(post.getId(), 0, "image.jpg", MediaType.IMAGE, null)));
        when(likeRepository.findLikedPostIds(viewerId, List.of(post.getId())))
                .thenReturn(List.of(post.getId()));
        when(fileStorageService.getPresignUrl("image.jpg")).thenReturn("signed-image.jpg");

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(0, 10, viewerId);

        assertEquals(1, response.getTotalElements());
        assertEquals("followed", response.getContent().getFirst().getUsername());
        assertEquals("signed-image.jpg", response.getContent().getFirst().getMedia().getFirst().url());
        assertTrue(response.getContent().getFirst().isLiked());
        verify(postPaginationRepository).findFollowingPosts(pageable, viewerId);
    }

    @Test
    void feedCarriesTheAuthorsProfilePictureResolvedOncePerAuthor() {
        UUID viewerId = UUID.randomUUID();
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        author.setUsername("followed");
        author.setProfileImage(author.getId() + "/PROFILE_PICTURE/me.jpg");
        PostEntity first = PostEntity.builder().id(UUID.randomUUID()).user(author).caption("a").createdAt(LocalDateTime.now()).build();
        PostEntity second = PostEntity.builder().id(UUID.randomUUID()).user(author).caption("b").createdAt(LocalDateTime.now()).build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(postPaginationRepository.findFollowingPosts(pageable, viewerId))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(profileImageUrlResolver.resolve(author.getProfileImage())).thenReturn("https://signed/me.jpg");

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(0, 10, viewerId);

        assertEquals("https://signed/me.jpg", response.getContent().get(0).getProfileImageUrl());
        assertEquals("https://signed/me.jpg", response.getContent().get(1).getProfileImageUrl());
        verify(profileImageUrlResolver, times(1)).resolve(author.getProfileImage());
    }

    @Test
    void numberedPageHandsOutTheCursorOfItsLastPost() {
        UUID viewerId = UUID.randomUUID();
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(author).caption("a")
                .createdAt(LocalDateTime.of(2026, 11, 18, 10, 0, 0, 123456000)).build();
        PageRequest pageable = PageRequest.of(0, 1);
        when(postPaginationRepository.findFollowingPosts(pageable, viewerId))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 5));

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(0, 1, viewerId);

        assertEquals(new FeedCursor(post.getCreatedAt(), post.getId()).encode(), response.getNextCursor());
    }

    @Test
    void cursorPageReadsOneRowMoreToKnowWhetherAnotherPageFollows() {
        UUID viewerId = UUID.randomUUID();
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 10, 0);
        PostEntity p1 = PostEntity.builder().id(UUID.randomUUID()).user(author).caption("1").createdAt(now.minusMinutes(1)).build();
        PostEntity p2 = PostEntity.builder().id(UUID.randomUUID()).user(author).caption("2").createdAt(now.minusMinutes(2)).build();
        PostEntity p3 = PostEntity.builder().id(UUID.randomUUID()).user(author).caption("3").createdAt(now.minusMinutes(3)).build();
        FeedCursor after = new FeedCursor(now, UUID.randomUUID());
        when(postPaginationRepository.findFollowingPostsAfter(viewerId, after.createdAt(), after.id(), PageRequest.of(0, 3)))
                .thenReturn(List.of(p1, p2, p3));

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(0, 2, after.encode(), viewerId);

        assertEquals(2, response.getContent().size());
        assertFalse(response.isLast());
        assertEquals(-1, response.getTotalElements());
        assertEquals(new FeedCursor(p2.getCreatedAt(), p2.getId()).encode(), response.getNextCursor());
        verify(postPaginationRepository, never()).findFollowingPosts(any(), any());
    }

    @Test
    void lastCursorPageHasNoNextCursor() {
        UUID viewerId = UUID.randomUUID();
        FeedCursor after = new FeedCursor(LocalDateTime.now(), UUID.randomUUID());
        when(postPaginationRepository.findExplorePostsAfter(false, viewerId, after.createdAt(), after.id(), PageRequest.of(0, 11)))
                .thenReturn(List.of());

        PaginatedResponse<PostFeedResponse> response = postService.getExplorePosts(0, 10, after.encode(), viewerId, false);

        assertTrue(response.isLast());
        assertNull(response.getNextCursor());
    }

    // ------------------------------------------------------------ ranked explore and home suggestions

    @Test
    void explore_whenTheRankingRunsOutTheOlderPostsFillThePageByDate() {
        UUID viewerId = UUID.randomUUID();
        UserEntity author = someone();
        PostEntity ranked = post(author, WINDOW_START.plusDays(1));
        PostEntity older1 = post(author, WINDOW_START.minusDays(1));
        PostEntity older2 = post(author, WINDOW_START.minusDays(2));
        when(rankedFeedService.start(viewerId, false, 0, 2))
                .thenReturn(Optional.of(new RankedFeedService.Slice(List.of(ranked), null, WINDOW_START)));
        // strictly below the ranked window, one row more than the room left
        when(postPaginationRepository.findExplorePostsAfter(false, viewerId, WINDOW_START, new UUID(0L, 0L), PageRequest.of(0, 2)))
                .thenReturn(List.of(older1, older2));

        PaginatedResponse<PostFeedResponse> response = postService.getExplorePosts(0, 2, viewerId, false);

        assertEquals(List.of(ranked.getId(), older1.getId()), response.getContent().stream().map(PostFeedResponse::getId).toList());
        assertFalse(response.isLast());
        assertEquals(new FeedCursor(older1.getCreatedAt(), older1.getId()).encode(), response.getNextCursor());
    }

    @Test
    void explore_aRankedCursorGoesOnWithTheSameRanking() {
        UUID viewerId = UUID.randomUUID();
        RankedCursor cursor = new RankedCursor(UUID.randomUUID(), 10, WINDOW_START);
        RankedCursor next = new RankedCursor(cursor.snapshotId(), 20, WINDOW_START);
        PostEntity post = post(someone(), WINDOW_START.plusDays(1));
        when(rankedFeedService.resume(cursor, viewerId, 10))
                .thenReturn(Optional.of(new RankedFeedService.Slice(List.of(post), next, WINDOW_START)));

        PaginatedResponse<PostFeedResponse> response = postService.getExplorePosts(0, 10, cursor.encode(), viewerId, false);

        assertEquals(post.getId(), response.getContent().getFirst().getId());
        assertEquals(next.encode(), response.getNextCursor());
        verify(rankedFeedService, never()).start(any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void explore_anExpiredRankingGoesOnByDateBelowIt() {
        UUID viewerId = UUID.randomUUID();
        RankedCursor cursor = new RankedCursor(UUID.randomUUID(), 10, WINDOW_START);
        when(rankedFeedService.resume(cursor, viewerId, 10)).thenReturn(Optional.empty());

        PaginatedResponse<PostFeedResponse> response = postService.getExplorePosts(0, 10, cursor.encode(), viewerId, false);

        assertTrue(response.isLast());
        // nothing of the ranking can come again: only posts older than its window
        verify(postPaginationRepository).findExplorePostsAfter(false, viewerId, WINDOW_START, new UUID(0L, 0L), PageRequest.of(0, 11));
    }

    @Test
    void explore_withoutRedisFallsBackToTheDateOrder() {
        UUID viewerId = UUID.randomUUID();
        PostEntity post = post(someone(), LocalDateTime.now());
        when(rankedFeedService.start(viewerId, false, 0, 10)).thenReturn(Optional.empty());
        when(postPaginationRepository.findExplorePostsAfter(eq(false), eq(viewerId), any(), any(), eq(PageRequest.of(0, 11))))
                .thenReturn(List.of(post));

        PaginatedResponse<PostFeedResponse> response = postService.getExplorePosts(0, 10, viewerId, false);

        assertEquals(post.getId(), response.getContent().getFirst().getId());
        assertTrue(response.isLast());
    }

    @Test
    void explore_aNumberedPageIsCutFurtherIntoAFreshRanking() {
        UUID viewerId = UUID.randomUUID();
        when(rankedFeedService.start(viewerId, false, 20, 10))
                .thenReturn(Optional.of(new RankedFeedService.Slice(List.of(), null, WINDOW_START)));

        postService.getExplorePosts(2, 10, viewerId, false);

        verify(rankedFeedService).start(viewerId, false, 20, 10);
    }

    @Test
    void home_whenItsPostsRunOutSuggestionsFillThePageAndAreMarked() {
        UUID viewerId = UUID.randomUUID();
        PostEntity followed = post(someone(), LocalDateTime.now());
        PostEntity suggestion = post(someone(), LocalDateTime.now());
        RankedCursor next = new RankedCursor(UUID.randomUUID(), 9, WINDOW_START);
        PageRequest pageable = PageRequest.of(0, 10);
        when(postPaginationRepository.findFollowingPosts(pageable, viewerId))
                .thenReturn(new PageImpl<>(List.of(followed), pageable, 1));
        when(rankedFeedService.start(viewerId, false, 0, 9))
                .thenReturn(Optional.of(new RankedFeedService.Slice(List.of(suggestion), next, WINDOW_START)));

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(0, 10, viewerId);

        assertEquals(2, response.getContent().size());
        assertFalse(response.getContent().get(0).isSuggested());
        assertTrue(response.getContent().get(1).isSuggested());
        assertEquals(1, response.getTotalElements());
        assertFalse(response.isLast());
        assertEquals(next.encode(), response.getNextCursor());
    }

    @Test
    void home_aFullCursorPageGetsNoSuggestions() {
        UUID viewerId = UUID.randomUUID();
        UserEntity author = someone();
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 10, 0);
        FeedCursor after = new FeedCursor(now, UUID.randomUUID());
        when(postPaginationRepository.findFollowingPostsAfter(viewerId, after.createdAt(), after.id(), PageRequest.of(0, 2)))
                .thenReturn(List.of(post(author, now.minusMinutes(1)), post(author, now.minusMinutes(2))));

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(0, 1, after.encode(), viewerId);

        assertEquals(1, response.getContent().size());
        assertFalse(response.isLast());
        verifyNoInteractions(rankedFeedService);
    }

    @Test
    void home_theLastCursorPageIsFollowedBySuggestions() {
        UUID viewerId = UUID.randomUUID();
        FeedCursor after = new FeedCursor(LocalDateTime.now(), UUID.randomUUID());
        PostEntity suggestion = post(someone(), LocalDateTime.now());
        when(postPaginationRepository.findFollowingPostsAfter(viewerId, after.createdAt(), after.id(), PageRequest.of(0, 11)))
                .thenReturn(List.of());
        when(rankedFeedService.start(viewerId, false, 0, 10))
                .thenReturn(Optional.of(new RankedFeedService.Slice(List.of(suggestion), null, WINDOW_START)));

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(0, 10, after.encode(), viewerId);

        assertTrue(response.getContent().getFirst().isSuggested());
        // the suggestions end with their ranking
        assertTrue(response.isLast());
        assertNull(response.getNextCursor());
    }

    @Test
    void home_aNumberedPageAfterTheFirstGetsNoSuggestions() {
        UUID viewerId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(1, 10);
        when(postPaginationRepository.findFollowingPosts(pageable, viewerId)).thenReturn(Page.empty(pageable));

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(1, 10, viewerId);

        assertTrue(response.isLast());
        verifyNoInteractions(rankedFeedService);
    }

    @Test
    void home_suggestionsGoOnFromTheirRankingAndEndWhenItExpired() {
        UUID viewerId = UUID.randomUUID();
        RankedCursor cursor = new RankedCursor(UUID.randomUUID(), 10, WINDOW_START);
        PostEntity suggestion = post(someone(), LocalDateTime.now());
        when(rankedFeedService.resume(cursor, viewerId, 10))
                .thenReturn(Optional.of(new RankedFeedService.Slice(List.of(suggestion), null, WINDOW_START)))
                .thenReturn(Optional.empty());

        PaginatedResponse<PostFeedResponse> first = postService.getFollowingPosts(0, 10, cursor.encode(), viewerId);
        PaginatedResponse<PostFeedResponse> expired = postService.getFollowingPosts(0, 10, cursor.encode(), viewerId);

        assertTrue(first.getContent().getFirst().isSuggested());
        assertTrue(expired.getContent().isEmpty());
        assertTrue(expired.isLast());
        verify(postPaginationRepository, never()).findExplorePostsAfter(anyBoolean(), any(), any(), any(), any());
    }

    @Test
    void aMangledCursorIsABadRequest() {
        UUID viewerId = UUID.randomUUID();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> postService.getFollowingPosts(0, 10, "not-a-cursor", viewerId));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        verifyNoInteractions(postPaginationRepository);
    }

    @Test
    void addNewPost_isRefusedWhenTheSizeCannotBeChecked() {
        UUID userId = UUID.randomUUID();
        String objectKey = userId + "/POST_FEED/" + UUID.randomUUID() + ".jpg";
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(author(userId)));
        when(fileStorageService.statObject(objectKey))
                .thenThrow(new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postService.addNewPost(userId, request("caption", objectKey)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getHttpStatus());
        verify(fileStorageService, times(3)).statObject(objectKey);
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testFollowingPosts_InvalidPaginationRejected() {
        UUID viewerId = UUID.randomUUID();
        assertThrows(BusinessException.class, () -> postService.getFollowingPosts(-1, 10, viewerId));
        assertThrows(BusinessException.class, () -> postService.getFollowingPosts(0, 0, viewerId));
        assertThrows(BusinessException.class, () -> postService.getFollowingPosts(0, 101, viewerId));
        verifyNoInteractions(postPaginationRepository);
    }

    @Test
    void testCreateUploadUrl_Success() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findActiveById(userId))
                .thenReturn(Optional.of(user));
        when(fileStorageService.getPresignUploadUrl(anyString(), eq("video/mp4")))
                .thenReturn("http://minio/presigned-url");

        // Act
        CreatePostUploadUrlResponse response = postService.createUploadUrl(
                userId, CreatePostUploadUrlRequest.builder().contentType("video/mp4").build());

        // Assert
        assertNotNull(response);
        assertEquals("http://minio/presigned-url", response.getPresignedUrl());
        assertTrue(response.getObjectKey().startsWith(userId + "/POST_FEED/"));
        assertTrue(response.getObjectKey().endsWith(".mp4"));
        assertEquals(900L, response.getExpiresInSeconds());
        // a video's POST form is capped at 50 MB by storage itself
        verify(fileStorageService).getPresignUploadForm(response.getObjectKey(), "video/mp4", 50L * 1024 * 1024);
        verify(fileStorageService).getPresignUploadUrl(eq(response.getObjectKey()), eq("video/mp4"));
    }

    @Test
    void createUploadUrl_capsAnImageAtFiveMegabytes() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(author(userId)));

        CreatePostUploadUrlResponse response = postService.createUploadUrl(
                userId, CreatePostUploadUrlRequest.builder().contentType("image/webp").build());

        verify(fileStorageService).getPresignUploadForm(response.getObjectKey(), "image/webp", 5L * 1024 * 1024);
    }

    @Test
    void testCreateUploadUrl_UnsupportedContentType_ThrowsBadRequest() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findActiveById(userId))
                .thenReturn(Optional.of(user));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> postService.createUploadUrl(
                        userId, CreatePostUploadUrlRequest.builder().contentType("application/pdf").build()));
        assertEquals(GlobalMessage.BAD_REQUEST.getMessage(), ex.getMessage());
        verify(fileStorageService, never()).getPresignUploadUrl(anyString(), anyString());
    }

    @Test
    void testAddNewPost_Success() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        String objectKey = userId + "/POST_FEED/" + UUID.randomUUID() + ".jpg";
        AddPostRequest request = request("My first post", objectKey);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@mail.com");

        when(userRepository.findActiveById(userId))
                .thenReturn(Optional.of(user));
        when(fileStorageService.statObject(objectKey))
                .thenReturn(ObjectStat.builder().size(1024L).contentType("image/jpeg").build());

        PostEntity postEntity = PostEntity.builder()
                .id(UUID.randomUUID())
                .caption("My first post")
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();

        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(postEntity);
        when(postMediaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CreatePostResponse result = postService.addNewPost(userId, request);

        // Assert
        assertNotNull(result);
        assertEquals(GlobalMessage.CREATED.getMessage(), result.getStatus());
        verify(fileStorageService, times(1)).statObject(objectKey);
        verify(userRepository).findActiveById(userId);
        verify(postCrudRepository, times(1))
                .save(any(PostEntity.class));
        ArgumentCaptor<PostCreatedEvent> event = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(postEventPublisher).publishPostCreatedEvent(event.capture());
        assertEquals(userId, event.getValue().getAuthorId());
        assertEquals(1, event.getValue().getMediaCount());
    }

    private UserEntity author(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        return user;
    }

    @Test
    void addNewPost_captionOnly_savesPostWithoutMedia() {
        UUID userId = UUID.randomUUID();
        UserEntity user = author(userId);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(PostEntity.builder()
                .id(UUID.randomUUID()).caption("just words").user(user).createdAt(LocalDateTime.now()).build());

        CreatePostResponse result = postService.addNewPost(userId, AddPostRequest.builder().caption("just words").build());

        assertEquals(GlobalMessage.CREATED.getMessage(), result.getStatus());
        verifyNoInteractions(fileStorageService, postMediaRepository);
        ArgumentCaptor<PostCreatedEvent> event = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(postEventPublisher).publishPostCreatedEvent(event.capture());
        assertEquals(0, event.getValue().getMediaCount());
        verify(postEventPublisher, never()).publishVideoThumbnailRequested(any());
    }

    @Test
    void addNewPost_mixedMedia_keepsOrderAndRequestsThumbnailPerVideo() {
        UUID userId = UUID.randomUUID();
        UserEntity user = author(userId);
        String photo = userId + "/POST_FEED/a.jpg";
        String clip1 = userId + "/POST_FEED/b.mp4";
        String clip2 = userId + "/POST_FEED/c.mov";
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.statObject(anyString()))
                .thenReturn(ObjectStat.builder().size(1024L).contentType("x").build());
        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(PostEntity.builder()
                .id(UUID.randomUUID()).user(user).createdAt(LocalDateTime.now()).build());
        when(postMediaRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<PostMediaEntity> items = inv.getArgument(0);
            items.forEach(item -> item.setId(UUID.randomUUID()));
            return items;
        });

        postService.addNewPost(userId, request(null, photo, clip1, clip2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostMediaEntity>> saved = ArgumentCaptor.forClass(List.class);
        verify(postMediaRepository).saveAll(saved.capture());
        assertEquals(List.of(photo, clip1, clip2), saved.getValue().stream().map(PostMediaEntity::getMediaKey).toList());
        assertEquals(List.of(0, 1, 2), saved.getValue().stream().map(PostMediaEntity::getPosition).toList());
        assertEquals(List.of(MediaType.IMAGE, MediaType.VIDEO, MediaType.VIDEO),
                saved.getValue().stream().map(PostMediaEntity::getMediaType).toList());
        verify(postEventPublisher, times(2)).publishVideoThumbnailRequested(any());
        // no caption: the email gets a title that says what the post holds
        ArgumentCaptor<PostCreatedEvent> created = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(postEventPublisher).publishPostCreatedEvent(created.capture());
        assertEquals("Untitled post (3 media)", created.getValue().getEmailProps().get("postTitle"));
    }

    @Test
    void addNewPost_noCaptionAndNoMedia_isRejected() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(author(userId)));

        for (AddPostRequest bad : List.of(new AddPostRequest(), request("   "))) {
            BusinessException ex = assertThrows(BusinessException.class, () -> postService.addNewPost(userId, bad));
            assertEquals(GlobalMessage.BAD_REQUEST.getMessage(), ex.getMessage());
        }
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void addNewPost_moreThanFiveMedia_isRejected() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(author(userId)));
        String[] keys = new String[6];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = userId + "/POST_FEED/" + i + ".jpg";
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postService.addNewPost(userId, request("caption", keys)));

        assertEquals(GlobalMessage.BAD_REQUEST.getMessage(), ex.getMessage());
        verify(fileStorageService, never()).statObject(anyString());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void addNewPost_duplicateMediaKey_isRejected() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(author(userId)));

        assertThrows(BusinessException.class, () -> postService.addNewPost(userId,
                request("caption", userId + "/POST_FEED/a.jpg", userId + "/POST_FEED/a.jpg")));
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void addNewPost_captionOver500Chars_isRejected() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(author(userId)));

        assertThrows(BusinessException.class,
                () -> postService.addNewPost(userId, request("a".repeat(501))));
        verify(postCrudRepository, never()).save(any());

        // exactly 500 is fine
        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(PostEntity.builder()
                .id(UUID.randomUUID()).user(author(userId)).createdAt(LocalDateTime.now()).build());
        postService.addNewPost(userId, request("a".repeat(500)));
        verify(postCrudRepository).save(any());
    }

    @Test
    void addNewPost_secondMediaOwnedByOtherUser_savesNothing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(author(userId)));
        when(fileStorageService.statObject(userId + "/POST_FEED/a.jpg"))
                .thenReturn(ObjectStat.builder().size(1024L).contentType("image/jpeg").build());

        BusinessException ex = assertThrows(BusinessException.class, () -> postService.addNewPost(userId,
                request("caption", userId + "/POST_FEED/a.jpg", "other/POST_FEED/b.jpg")));

        assertEquals(GlobalMessage.ACCESS_DENIED.getMessage(), ex.getMessage());
        verify(postCrudRepository, never()).save(any());
        verifyNoInteractions(postMediaRepository);
    }

    @Test
    void addNewPost_requestsThumbnailForVideo() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        String objectKey = userId + "/POST_FEED/clip.mp4";
        AddPostRequest request = request("clip", objectKey);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.statObject(objectKey))
                .thenReturn(ObjectStat.builder().size(1024L).contentType("video/mp4").build());
        PostEntity saved = PostEntity.builder().id(UUID.randomUUID()).caption("clip")
                .user(user).createdAt(LocalDateTime.now()).build();
        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(saved);
        when(postMediaRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<PostMediaEntity> items = inv.getArgument(0);
            items.forEach(item -> item.setId(UUID.randomUUID()));
            return items;
        });

        postService.addNewPost(userId, request);

        ArgumentCaptor<VideoThumbnailRequestedEvent> requested =
                ArgumentCaptor.forClass(VideoThumbnailRequestedEvent.class);
        verify(postEventPublisher).publishVideoThumbnailRequested(requested.capture());
        assertEquals(saved.getId(), requested.getValue().getPostId());
        assertNotNull(requested.getValue().getMediaId());
        assertEquals(userId + "/POST_FEED/clip_thumb.jpg", requested.getValue().getThumbnailObjectKey());
    }

    @Test
    void addNewPost_doesNotRequestThumbnailForImage() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        String objectKey = userId + "/POST_FEED/photo.jpg";
        AddPostRequest request = request(null, objectKey);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.statObject(objectKey))
                .thenReturn(ObjectStat.builder().size(1024L).contentType("image/jpeg").build());
        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(PostEntity.builder()
                .id(UUID.randomUUID()).user(user).createdAt(LocalDateTime.now()).build());
        when(postMediaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        postService.addNewPost(userId, request);

        verify(postEventPublisher, never()).publishVideoThumbnailRequested(any());
    }

    @Test
    void testGetAllPostByUserId_Success() {
        // Arrange
        int page = 0;
        int size = 2;
        String username = "testuser";
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername(username);
        UUID viewerId = UUID.randomUUID();

        PostEntity p1 = PostEntity.builder().id(UUID.randomUUID()).user(user).caption("hello")
                .createdAt(LocalDateTime.now()).build();
        List<PostEntity> posts = List.of(p1);
        Page<PostEntity> postPage = new PageImpl<>(posts, PageRequest.of(page, size), posts.size());

        when(userRepository.findActiveByUsername(username)).thenReturn(Optional.of(user));
        when(postPaginationRepository.findAllActivePostsByUserId(any(Pageable.class), eq(userId), eq(false), any()))
                .thenReturn(postPage);
        when(likeRepository.findLikedPostIds(viewerId, List.of(p1.getId())))
                .thenReturn(List.of(p1.getId()));

        // Act
        PaginatedResponse<PostFeedResponse> response = postService.getAllPostByUserId(page, size, username, viewerId, false);

        // Assert
        assertNotNull(response);
        assertEquals(username, response.getContent().get(0).getUsername());
        assertTrue(response.getContent().get(0).isLiked());
        verify(userRepository).findActiveByUsername(username);
        verify(postPaginationRepository).findAllActivePostsByUserId(any(Pageable.class), eq(userId), eq(false), any());
    }

    @Test
    void testGetAllPostByUserId_UserNotFound_ThrowsBusinessException() {
        // Arrange
        String username = "nouser";
        when(userRepository.findActiveByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> postService.getAllPostByUserId(0, 10, username, UUID.randomUUID(), false));
        assertEquals(GlobalMessage.USER_NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    void testMediaOnly_PassedToRepository() {
        UUID viewerId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("author");
        when(userRepository.findActiveByUsername("author")).thenReturn(Optional.of(user));
        when(rankedFeedService.start(viewerId, true, 0, 10))
                .thenReturn(Optional.of(new RankedFeedService.Slice(List.of(), null, WINDOW_START)));
        when(postPaginationRepository.findAllActivePostsByUserId(any(Pageable.class), eq(user.getId()), eq(true), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        postService.getExplorePosts(0, 10, viewerId, true);
        postService.getAllPostByUserId(0, 10, "author", viewerId, true);

        verify(postPaginationRepository).findExplorePostsAfter(true, viewerId, WINDOW_START, new UUID(0L, 0L), PageRequest.of(0, 11));
        verify(postPaginationRepository).findAllActivePostsByUserId(any(Pageable.class), eq(user.getId()), eq(true), any());
    }

    @Test
    void testAddNewPost_UserNotFound_ThrowsBadRequest() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AddPostRequest request = request("caption", userId + "/POST_FEED/x.jpg");

        when(userRepository.findActiveById(userId))
                .thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () ->
                postService.addNewPost(userId, request));
        assertEquals(GlobalMessage.BAD_REQUEST.getMessage(), ex.getMessage());
        verify(fileStorageService, never()).statObject(anyString());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testAddNewPost_BlankObjectKey_ThrowsBadRequest() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AddPostRequest request = request("caption", " ");

        when(userRepository.findActiveById(userId))
                .thenReturn(Optional.of(new UserEntity()));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () ->
                postService.addNewPost(userId, request));
        assertEquals(GlobalMessage.BAD_REQUEST.getMessage(), ex.getMessage());
        verify(fileStorageService, never()).statObject(anyString());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testAddNewPost_ObjectKeyOwnedByOtherUser_ThrowsAccessDenied() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AddPostRequest request = request("caption", "otheruser/POST_FEED/" + UUID.randomUUID() + ".jpg");

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findActiveById(userId))
                .thenReturn(Optional.of(user));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () ->
                postService.addNewPost(userId, request));
        assertEquals(GlobalMessage.ACCESS_DENIED.getMessage(), ex.getMessage());
        verify(fileStorageService, never()).statObject(anyString());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void addNewPost_keyUnderTheUsername_isRejected() {
        // media is filed under the user id; a key under a username may belong to whoever held that username before
        UUID userId = UUID.randomUUID();
        UserEntity user = author(userId);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                postService.addNewPost(userId, request("caption", "testuser/POST_FEED/" + UUID.randomUUID() + ".jpg")));
        assertEquals(GlobalMessage.ACCESS_DENIED.getMessage(), ex.getMessage());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testAddNewPost_ImageTooLarge_ThrowsBadRequestFileTooLarge() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String objectKey = userId + "/POST_FEED/" + UUID.randomUUID() + ".jpg";
        AddPostRequest request = request("caption", objectKey);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findActiveById(userId))
                .thenReturn(Optional.of(user));
        when(fileStorageService.statObject(objectKey))
                .thenReturn(ObjectStat.builder()
                        .size(6L * 1024 * 1024) // 6MB
                        .contentType("image/jpeg")
                        .build());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () ->
                postService.addNewPost(userId, request));
        assertEquals(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE.getMessage(), ex.getMessage());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testAddNewPost_VideoTooLarge_ThrowsBadRequestFileTooLarge() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String objectKey = userId + "/POST_FEED/" + UUID.randomUUID() + ".mp4";
        AddPostRequest request = request("caption", objectKey);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findActiveById(userId))
                .thenReturn(Optional.of(user));
        when(fileStorageService.statObject(objectKey))
                .thenReturn(ObjectStat.builder()
                        .size(51L * 1024 * 1024) // 51MB
                        .contentType("video/mp4")
                        .build());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () ->
                postService.addNewPost(userId, request));
        assertEquals(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE.getMessage(), ex.getMessage());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testAddNewPost_UnsupportedObjectType_ThrowsBadRequest() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String objectKey = userId + "/POST_FEED/" + UUID.randomUUID() + ".bin";
        AddPostRequest request = request("caption", objectKey);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findActiveById(userId))
                .thenReturn(Optional.of(user));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () ->
                postService.addNewPost(userId, request));
        assertEquals(GlobalMessage.BAD_REQUEST.getMessage(), ex.getMessage());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testDeletePost_OwnActivePost_SoftDeletes() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        PostEntity post = PostEntity.builder()
                .id(postId)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();

        when(postCrudRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(Optional.of(post));
        when(postMediaRepository.findByPostIdOrderByPositionAsc(postId)).thenReturn(List.of(
                media(postId, 0, "object/a.jpg", MediaType.IMAGE, null),
                media(postId, 1, "object/b.mp4", MediaType.VIDEO, "object/b_thumb.jpg")));

        Operation result = postService.deletePost(postId, userId);

        assertEquals(Operation.DELETED, result);
        assertTrue(post.isDeleted());
        verify(postCrudRepository).save(post);
        // the rows go with the post, the files follow in the background
        verify(postMediaRepository).deleteAllByPostId(postId);
        verify(mediaCleanupService).deleteObjects(List.of("object/a.jpg", "object/b.mp4", "object/b_thumb.jpg"));
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void testDeletePost_OtherUsersPost_ThrowsAccessDenied() {
        UUID postId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(UUID.randomUUID());
        PostEntity post = PostEntity.builder()
                .id(postId)
                .user(owner)
                .createdAt(LocalDateTime.now())
                .build();

        when(postCrudRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(Optional.of(post));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.deletePost(postId, UUID.randomUUID()));

        assertEquals(GlobalMessage.ACCESS_DENIED.getMessage(), exception.getMessage());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void feed_captionOnlyPost_hasEmptyMedia() throws Exception {
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        author.setUsername("author");
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(author).caption("hello")
                .createdAt(LocalDateTime.now()).build();
        rankedPage(post);

        PostFeedResponse item = postService.getExplorePosts(0, 10, UUID.randomUUID(), false).getContent().get(0);

        assertTrue(item.getMedia().isEmpty());
        verify(fileStorageService, never()).getPresignUrl(any());
    }

    // ------------------------------------------------------------ takedown in the feed

    private PostEntity takenDownPost(UserEntity author) {
        return PostEntity.builder().id(UUID.randomUUID()).user(author).caption("secret caption")
                .createdAt(LocalDateTime.now())
                .moderationStatus(ModerationStatus.TAKEN_DOWN)
                .moderatedAt(Instant.parse("2026-11-08T01:00:00Z"))
                .moderationReason("melanggar aturan komunitas")
                .build();
    }

    @Test
    void feed_takenDownPost_isListedWithStatusButWithoutContentForOtherUsers() throws Exception {
        UUID viewerId = UUID.randomUUID();
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        author.setUsername("author");
        PostEntity post = takenDownPost(author);
        when(postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(any()))
                .thenReturn(List.of(media(post.getId(), 0, "media.jpg", MediaType.VIDEO, "thumb.jpg")));
        // explore leaves taken down posts out; the home feed lists them
        when(postPaginationRepository.findFollowingPosts(PageRequest.of(0, 10), viewerId))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));

        PostFeedResponse item = postService.getFollowingPosts(0, 10, viewerId).getContent().get(0);

        assertEquals(ModerationStatus.TAKEN_DOWN, item.getModerationStatus());
        assertEquals(Instant.parse("2026-11-08T01:00:00Z"), item.getTakenDownAt());
        assertNull(item.getTakedownReason());
        assertNull(item.getCaption());
        assertTrue(item.getMedia().isEmpty());
        verify(fileStorageService, never()).getPresignUrl(any());
    }

    @Test
    void feed_takenDownPost_authorStillSeesHisContentAndTheReason() throws Exception {
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        author.setUsername("author");
        PostEntity post = takenDownPost(author);
        when(postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(any()))
                .thenReturn(List.of(media(post.getId(), 0, "media.jpg", MediaType.VIDEO, "thumb.jpg")));
        when(postPaginationRepository.findAllActivePostsByUserId(any(Pageable.class), eq(author.getId()), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));
        when(userRepository.findActiveByUsername("author")).thenReturn(Optional.of(author));
        when(fileStorageService.getPresignUrl("media.jpg")).thenReturn("signed-media");
        when(fileStorageService.getPresignUrl("thumb.jpg")).thenReturn("signed-thumb");

        PostFeedResponse item = postService.getAllPostByUserId(0, 10, "author", author.getId(), false).getContent().get(0);

        assertEquals(ModerationStatus.TAKEN_DOWN, item.getModerationStatus());
        assertEquals("melanggar aturan komunitas", item.getTakedownReason());
        assertEquals("secret caption", item.getCaption());
        assertEquals("signed-media", item.getMedia().get(0).url());
        assertEquals("signed-thumb", item.getMedia().get(0).thumbnailUrl());
    }

    @Test
    void feed_activePost_hasActiveStatusAndNoTakedownFields() throws Exception {
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        author.setUsername("author");
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(author).caption("hello")
                .createdAt(LocalDateTime.now()).build();
        rankedPage(post);
        when(postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(any()))
                .thenReturn(List.of(media(post.getId(), 0, "img.jpg", MediaType.IMAGE, null)));
        when(fileStorageService.getPresignUrl("img.jpg")).thenReturn("signed-img");

        PostFeedResponse item = postService.getExplorePosts(0, 10, UUID.randomUUID(), false).getContent().get(0);

        assertEquals(ModerationStatus.ACTIVE, item.getModerationStatus());
        assertNull(item.getTakedownReason());
        assertNull(item.getTakenDownAt());
        assertEquals("hello", item.getCaption());
        assertEquals("signed-img", item.getMedia().get(0).url());
    }

    @Test
    void feedsRejectAPageOrSizeOutsideTheLimitsWithoutTouchingTheDatabase() {
        UUID userId = UUID.randomUUID();

        assertThrows(BusinessException.class, () -> postService.getExplorePosts(-1, 10, userId, false));
        assertThrows(BusinessException.class, () -> postService.getExplorePosts(0, 0, userId, false));
        assertThrows(BusinessException.class, () -> postService.getExplorePosts(0, 51, userId, false));
        assertThrows(BusinessException.class, () -> postService.getFollowingPosts(0, 51, userId));
        assertThrows(BusinessException.class, () -> postService.getAllPostByUserId(-1, 10, "someone", userId, false));
        assertThrows(BusinessException.class, () -> postService.getAllPostByUserId(0, 100000, "someone", userId, false));
        verifyNoInteractions(postPaginationRepository, userRepository, rankedFeedService);
    }

    // ------------------------------------------------------------ one post by id

    private UserEntity authorEntity() {
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        author.setUsername("author");
        return author;
    }

    @Test
    void getPostById_returnsAMediaPostWithCountsAndPresignedMedia() throws Exception {
        UUID viewerId = UUID.randomUUID();
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(authorEntity()).caption("hello")
                .createdAt(LocalDateTime.now()).build();
        when(postPaginationRepository.findVisibleById(post.getId(), viewerId)).thenReturn(Optional.of(post));
        when(postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(any()))
                .thenReturn(List.of(media(post.getId(), 0, "img.jpg", MediaType.IMAGE, null)));
        when(fileStorageService.getPresignUrl("img.jpg")).thenReturn("signed-img");

        PostFeedResponse item = postService.getPostById(post.getId(), viewerId);

        assertEquals(post.getId(), item.getId());
        assertEquals("author", item.getUsername());
        assertEquals("hello", item.getCaption());
        assertEquals("signed-img", item.getMedia().get(0).url());
        assertEquals(ModerationStatus.ACTIVE, item.getModerationStatus());
    }

    @Test
    void getPostById_returnsACaptionOnlyPostWithoutMedia() throws Exception {
        UUID viewerId = UUID.randomUUID();
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(authorEntity()).caption("just words")
                .createdAt(LocalDateTime.now()).build();
        when(postPaginationRepository.findVisibleById(post.getId(), viewerId)).thenReturn(Optional.of(post));

        PostFeedResponse item = postService.getPostById(post.getId(), viewerId);

        assertEquals("just words", item.getCaption());
        assertTrue(item.getMedia().isEmpty());
        verify(fileStorageService, never()).getPresignUrl(any());
    }

    @Test
    void getPostById_aPostTheViewerCannotSeeIsNotFound() {
        UUID postId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        when(postPaginationRepository.findVisibleById(postId, viewerId)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> postService.getPostById(postId, viewerId));

        assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
    }

    @Test
    void getPostById_takenDownPostHidesItsContentFromOthers() {
        PostEntity post = takenDownPost(authorEntity());
        UUID viewerId = UUID.randomUUID();
        when(postPaginationRepository.findVisibleById(post.getId(), viewerId)).thenReturn(Optional.of(post));
        when(postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(any()))
                .thenReturn(List.of(media(post.getId(), 0, "media.jpg", MediaType.IMAGE, null)));

        PostFeedResponse item = postService.getPostById(post.getId(), viewerId);

        assertEquals(ModerationStatus.TAKEN_DOWN, item.getModerationStatus());
        assertNull(item.getCaption());
        assertNull(item.getTakedownReason());
        assertTrue(item.getMedia().isEmpty());
    }

    @Test
    void getPostById_takenDownPostShowsTheAuthorHisContentAndTheReason() throws Exception {
        UserEntity author = authorEntity();
        PostEntity post = takenDownPost(author);
        when(postPaginationRepository.findVisibleById(post.getId(), author.getId())).thenReturn(Optional.of(post));
        when(postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(any()))
                .thenReturn(List.of(media(post.getId(), 0, "media.jpg", MediaType.IMAGE, null)));
        when(fileStorageService.getPresignUrl("media.jpg")).thenReturn("signed-media");

        PostFeedResponse item = postService.getPostById(post.getId(), author.getId());

        assertEquals("secret caption", item.getCaption());
        assertEquals("melanggar aturan komunitas", item.getTakedownReason());
        assertEquals("signed-media", item.getMedia().get(0).url());
    }
}
