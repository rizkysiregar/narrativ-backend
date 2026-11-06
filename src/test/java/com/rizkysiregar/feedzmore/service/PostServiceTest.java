package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.messaging.event.VideoThumbnailRequestedEvent;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.messaging.publisher.PostEventPublisher;
import com.rizkysiregar.feedzmore.model.dto.ObjectStat;
import com.rizkysiregar.feedzmore.model.response.CreatePostResponse;
import com.rizkysiregar.feedzmore.model.response.CreatePostUploadUrlResponse;
import com.rizkysiregar.feedzmore.model.response.PostFeedResponse;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.request.CreatePostUploadUrlRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.*;
import com.rizkysiregar.feedzmore.service.impl.PostServiceImpl;
import com.rizkysiregar.feedzmore.util.Operation;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PostServiceTest {

    @Mock
    private PostPaginationRepository postPaginationRepository;
    @Mock
    private PostCrudRepository postCrudRepository;
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

    @InjectMocks
    private PostServiceImpl postService;

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
                .mediaUrl("img1.jpg").createdAt(LocalDateTime.now()).build();
        PostEntity p2 = PostEntity.builder().id(UUID.randomUUID()).user(bob).caption("world")
                .mediaUrl("img2.jpg").createdAt(LocalDateTime.now().minusMinutes(1)).build();
        List<PostEntity> posts = List.of(p1, p2);
        Page<PostEntity> postPage = new PageImpl<>(posts, PageRequest.of(page, size), posts.size());

        when(postPaginationRepository.findAllActivePosts(any(Pageable.class)))
                .thenReturn(postPage);

        try {
            when(fileStorageService.getPresignUrl("img1.jpg")).thenReturn("img1.jpg");
            when(fileStorageService.getPresignUrl("img2.jpg")).thenReturn("img2.jpg");
        } catch (Exception e) {
            // do nothing
        }

        // Act
        PaginatedResponse<PostFeedResponse> response = postService.getExplorePosts(page, size, userId);

        // Assert
        assertNotNull(response);
        assertEquals(page, response.getPageNumber());
        assertEquals(size, response.getPageSize());
        assertEquals(posts.size(), response.getContent().size());
        assertEquals(posts.size(), response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        // image url should be kept as is
        assertEquals("img1.jpg", response.getContent().get(0).getMediaUrl());
        assertEquals("img2.jpg", response.getContent().get(1).getMediaUrl());

        verify(postPaginationRepository, times(1))
                .findAllActivePosts(any(Pageable.class));
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
        verify(postPaginationRepository, never()).findAllActivePosts(any(Pageable.class));
        verifyNoInteractions(likeRepository, commentRepository, fileStorageService);
    }

    @Test
    void testFollowingPosts_UsesViewerLikeStatus() throws Exception {
        UUID viewerId = UUID.randomUUID();
        UserEntity author = new UserEntity();
        author.setUsername("followed");
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(author)
                .caption("hello").mediaUrl("image.jpg").createdAt(LocalDateTime.now()).build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(postPaginationRepository.findFollowingPosts(pageable, viewerId))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(likeRepository.findLikedPostIds(viewerId, List.of(post.getId())))
                .thenReturn(List.of(post.getId()));
        when(fileStorageService.getPresignUrl("image.jpg")).thenReturn("signed-image.jpg");

        PaginatedResponse<PostFeedResponse> response = postService.getFollowingPosts(0, 10, viewerId);

        assertEquals(1, response.getTotalElements());
        assertEquals("followed", response.getContent().getFirst().getUsername());
        assertEquals("signed-image.jpg", response.getContent().getFirst().getMediaUrl());
        assertTrue(response.getContent().getFirst().isLiked());
        verify(postPaginationRepository).findFollowingPosts(pageable, viewerId);
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

        when(userRepository.findByIdAndIsDeletedFalse(userId))
                .thenReturn(Optional.of(user));
        when(fileStorageService.getPresignUploadUrl(anyString(), eq("video/mp4")))
                .thenReturn("http://minio/presigned-url");

        // Act
        CreatePostUploadUrlResponse response = postService.createUploadUrl(
                userId, CreatePostUploadUrlRequest.builder().contentType("video/mp4").build());

        // Assert
        assertNotNull(response);
        assertEquals("http://minio/presigned-url", response.getPresignedUrl());
        assertTrue(response.getObjectKey().startsWith("testuser/POST_FEED/"));
        assertTrue(response.getObjectKey().endsWith(".mp4"));
        assertEquals(900L, response.getExpiresInSeconds());
        verify(fileStorageService).getPresignUploadUrl(eq(response.getObjectKey()), eq("video/mp4"));
    }

    @Test
    void testCreateUploadUrl_UnsupportedContentType_ThrowsBadRequest() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findByIdAndIsDeletedFalse(userId))
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
        String objectKey = "testuser/POST_FEED/" + UUID.randomUUID() + ".jpg";
        AddPostRequest request = AddPostRequest.builder()
                .caption("My first post")
                .objectKey(objectKey)
                .build();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@mail.com");

        when(userRepository.findByIdAndIsDeletedFalse(userId))
                .thenReturn(Optional.of(user));
        when(fileStorageService.statObject(objectKey))
                .thenReturn(ObjectStat.builder().size(1024L).contentType("image/jpeg").build());

        PostEntity postEntity = PostEntity.builder()
                .id(UUID.randomUUID())
                .caption("My first post")
                .mediaUrl(objectKey)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();

        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(postEntity);

        // Act
        CreatePostResponse result = postService.addNewPost(userId, request);

        // Assert
        assertNotNull(result);
        assertEquals(GlobalMessage.CREATED.getMessage(), result.getStatus());
        verify(fileStorageService, times(1)).statObject(objectKey);
        verify(userRepository).findByIdAndIsDeletedFalse(userId);
        verify(postCrudRepository, times(1))
                .save(any(PostEntity.class));
        ArgumentCaptor<PostCreatedEvent> event = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(postEventPublisher).publishPostCreatedEvent(event.capture());
        assertEquals(userId, event.getValue().getAuthorId());
    }

    @Test
    void addNewPost_requestsThumbnailForVideo() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        String objectKey = "testuser/POST_FEED/clip.mp4";
        AddPostRequest request = new AddPostRequest();
        request.setObjectKey(objectKey);
        request.setCaption("clip");
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.statObject(objectKey))
                .thenReturn(ObjectStat.builder().size(1024L).contentType("video/mp4").build());
        PostEntity saved = PostEntity.builder().id(UUID.randomUUID()).caption("clip").mediaUrl(objectKey)
                .mediaType(MediaType.VIDEO).user(user).createdAt(LocalDateTime.now()).build();
        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(saved);

        postService.addNewPost(userId, request);

        ArgumentCaptor<VideoThumbnailRequestedEvent> requested =
                ArgumentCaptor.forClass(VideoThumbnailRequestedEvent.class);
        verify(postEventPublisher).publishVideoThumbnailRequested(requested.capture());
        assertEquals(saved.getId(), requested.getValue().getPostId());
        assertEquals("testuser/POST_FEED/clip_thumb.jpg", requested.getValue().getThumbnailObjectKey());
    }

    @Test
    void addNewPost_doesNotRequestThumbnailForImage() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        String objectKey = "testuser/POST_FEED/photo.jpg";
        AddPostRequest request = new AddPostRequest();
        request.setObjectKey(objectKey);
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.statObject(objectKey))
                .thenReturn(ObjectStat.builder().size(1024L).contentType("image/jpeg").build());
        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(PostEntity.builder()
                .id(UUID.randomUUID()).mediaUrl(objectKey).mediaType(MediaType.IMAGE)
                .user(user).createdAt(LocalDateTime.now()).build());

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
                .mediaUrl("img1.jpg").createdAt(LocalDateTime.now()).build();
        List<PostEntity> posts = List.of(p1);
        Page<PostEntity> postPage = new PageImpl<>(posts, PageRequest.of(page, size), posts.size());

        when(userRepository.findByUsernameAndIsDeletedFalse(username)).thenReturn(Optional.of(user));
        when(postPaginationRepository.findAllActivePostsByUserId(any(Pageable.class), eq(userId)))
                .thenReturn(postPage);
        when(likeRepository.findLikedPostIds(viewerId, List.of(p1.getId())))
                .thenReturn(List.of(p1.getId()));

        // Act
        PaginatedResponse<PostFeedResponse> response = postService.getAllPostByUserId(page, size, username, viewerId);

        // Assert
        assertNotNull(response);
        assertEquals(username, response.getContent().get(0).getUsername());
        assertTrue(response.getContent().get(0).isLiked());
        verify(userRepository).findByUsernameAndIsDeletedFalse(username);
        verify(postPaginationRepository).findAllActivePostsByUserId(any(Pageable.class), eq(userId));
    }

    @Test
    void testGetAllPostByUserId_UserNotFound_ThrowsBusinessException() {
        // Arrange
        String username = "nouser";
        when(userRepository.findByUsernameAndIsDeletedFalse(username)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> postService.getAllPostByUserId(0, 10, username, UUID.randomUUID()));
        assertEquals(GlobalMessage.USER_NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    void testAddNewPost_UserNotFound_ThrowsBadRequest() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AddPostRequest request = AddPostRequest.builder()
                .caption("caption")
                .objectKey("testuser/POST_FEED/x.jpg")
                .build();

        when(userRepository.findByIdAndIsDeletedFalse(userId))
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
        AddPostRequest request = AddPostRequest.builder()
                .caption("caption")
                .objectKey(" ")
                .build();

        when(userRepository.findByIdAndIsDeletedFalse(userId))
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
        AddPostRequest request = AddPostRequest.builder()
                .caption("caption")
                .objectKey("otheruser/POST_FEED/" + UUID.randomUUID() + ".jpg")
                .build();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findByIdAndIsDeletedFalse(userId))
                .thenReturn(Optional.of(user));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () ->
                postService.addNewPost(userId, request));
        assertEquals(GlobalMessage.ACCESS_DENIED.getMessage(), ex.getMessage());
        verify(fileStorageService, never()).statObject(anyString());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testAddNewPost_ImageTooLarge_ThrowsBadRequestFileTooLarge() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String objectKey = "testuser/POST_FEED/" + UUID.randomUUID() + ".jpg";
        AddPostRequest request = AddPostRequest.builder()
                .caption("caption")
                .objectKey(objectKey)
                .build();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findByIdAndIsDeletedFalse(userId))
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
        String objectKey = "testuser/POST_FEED/" + UUID.randomUUID() + ".mp4";
        AddPostRequest request = AddPostRequest.builder()
                .caption("caption")
                .objectKey(objectKey)
                .build();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findByIdAndIsDeletedFalse(userId))
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
        String objectKey = "testuser/POST_FEED/" + UUID.randomUUID() + ".bin";
        AddPostRequest request = AddPostRequest.builder()
                .caption("caption")
                .objectKey(objectKey)
                .build();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findByIdAndIsDeletedFalse(userId))
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
                .mediaUrl("object/name.jpg")
                .createdAt(LocalDateTime.now())
                .build();

        when(postCrudRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(Optional.of(post));

        Operation result = postService.deletePost(postId, userId);

        assertEquals(Operation.DELETED, result);
        assertTrue(post.isDeleted());
        verify(postCrudRepository).save(post);
    }

    @Test
    void testDeletePost_OtherUsersPost_ThrowsAccessDenied() {
        UUID postId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(UUID.randomUUID());
        PostEntity post = PostEntity.builder()
                .id(postId)
                .user(owner)
                .mediaUrl("object/name.jpg")
                .createdAt(LocalDateTime.now())
                .build();

        when(postCrudRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(Optional.of(post));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.deletePost(postId, UUID.randomUUID()));

        assertEquals(GlobalMessage.ACCESS_DENIED.getMessage(), exception.getMessage());
        verify(postCrudRepository, never()).save(any());
    }
}
