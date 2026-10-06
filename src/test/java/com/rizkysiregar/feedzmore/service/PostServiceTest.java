package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.messaging.publisher.PostEventPublisher;
import com.rizkysiregar.feedzmore.model.response.CreatePostResponse;
import com.rizkysiregar.feedzmore.model.response.PostFeedResponse;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.*;
import com.rizkysiregar.feedzmore.service.impl.PostServiceImpl;
import com.rizkysiregar.feedzmore.util.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

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
    void testGetAllPost_ReturnsPaginatedResponse() {
        // Arrange
        int page = 0;
        int size = 2;
        UUID userId = UUID.randomUUID();
        UserEntity alice = new UserEntity();
        alice.setUsername("alice");
        UserEntity bob = new UserEntity();
        bob.setUsername("bob");
        PostEntity p1 = PostEntity.builder().id(UUID.randomUUID()).user(alice).caption("hello")
                .imageUrl("img1.jpg").createdAt(LocalDateTime.now()).build();
        PostEntity p2 = PostEntity.builder().id(UUID.randomUUID()).user(bob).caption("world")
                .imageUrl("img2.jpg").createdAt(LocalDateTime.now().minusMinutes(1)).build();
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
        PaginatedResponse<PostFeedResponse> response = postService.getAllPost(page, size, userId);

        // Assert
        assertNotNull(response);
        assertEquals(page, response.getPageNumber());
        assertEquals(size, response.getPageSize());
        assertEquals(posts.size(), response.getContent().size());
        assertEquals(posts.size(), response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        // image url should be kept as is
        assertEquals("img1.jpg", response.getContent().get(0).getImageUrl());
        assertEquals("img2.jpg", response.getContent().get(1).getImageUrl());

        verify(postPaginationRepository, times(1))
                .findAllActivePosts(any(Pageable.class));
    }

    @Test
    void testAddNewPost_Success() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        AddPostRequest request = AddPostRequest.builder()
                .caption("My first post")
                .build();

        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getSize()).thenReturn(1024L); // 1KB

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@mail.com");

        when(userRepository.findByIdAndIsDeletedFalse(userId))
                .thenReturn(Optional.of(user));
        when(fileStorageService.uploadFile(eq(image), eq("testuser"), anyString()))
                .thenReturn("object/name.jpg");

        PostEntity postEntity = PostEntity.builder()
                .id(UUID.randomUUID())
                .caption("My first post")
                .imageUrl("object/name.jpg")
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();

        when(postCrudRepository.save(any(PostEntity.class))).thenReturn(postEntity);

        // Act
        CreatePostResponse result = postService.addNewPost(userId, request, image);

        // Assert
        assertNotNull(result);
        assertEquals(GlobalMessage.CREATED.getMessage(), result.getStatus());
        verify(fileStorageService, times(1))
                .uploadFile(eq(image), eq("testuser"), anyString());
        verify(userRepository).findByIdAndIsDeletedFalse(userId);
        verify(postCrudRepository, times(1))
                .save(any(PostEntity.class));
        verify(postEventPublisher, times(1)).publishPostCreatedEvent(any());
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

        PostEntity p1 = PostEntity.builder().id(UUID.randomUUID()).user(user).caption("hello")
                .imageUrl("img1.jpg").createdAt(LocalDateTime.now()).build();
        List<PostEntity> posts = List.of(p1);
        Page<PostEntity> postPage = new PageImpl<>(posts, PageRequest.of(page, size), posts.size());

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(postPaginationRepository.findAllActivePostsByUserId(any(Pageable.class), eq(userId)))
                .thenReturn(postPage);

        // Act
        PaginatedResponse<PostFeedResponse> response = postService.getAllPostByUserId(page, size, username);

        // Assert
        assertNotNull(response);
        assertEquals(username, response.getContent().get(0).getUsername());
        verify(userRepository).findByUsername(username);
        verify(postPaginationRepository).findAllActivePostsByUserId(any(Pageable.class), eq(userId));
    }

    @Test
    void testGetAllPostByUserId_UserNotFound_ThrowsBusinessException() {
        // Arrange
        String username = "nouser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> postService.getAllPostByUserId(0, 10, username));
        assertEquals(GlobalMessage.USER_NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    void testAddNewPost_UserNotFound_ThrowsBadRequest() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        AddPostRequest request = AddPostRequest.builder()
                .caption("caption")
                .build();
        MultipartFile image = mock(MultipartFile.class);

        when(userRepository.findByIdAndIsDeletedFalse(userId))
                .thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            try {
                postService.addNewPost(userId, request, image);
            } catch (Exception e) {
                if (e instanceof BusinessException be) {
                    throw be;
                }
                fail("Unexpected exception type: " + e);
            }
        });
        assertEquals(GlobalMessage.BAD_REQUEST.getMessage(), ex.getMessage());
        verify(fileStorageService, never()).uploadFile(any(), anyString(), anyString());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testAddNewPost_EmptyImage_ThrowsBadRequest() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        AddPostRequest request = AddPostRequest.builder()
                .caption("caption")
                .build();

        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(true);

        when(userRepository.findByIdAndIsDeletedFalse(userId))
                .thenReturn(Optional.of(new UserEntity()));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            try {
                postService.addNewPost(userId, request, image);
            } catch (Exception e) {
                if (e instanceof BusinessException be) {
                    throw be;
                }
                fail("Unexpected exception type: " + e);
            }
        });
        assertEquals(GlobalMessage.BAD_REQUEST.getMessage(), ex.getMessage());
        verify(fileStorageService, never()).uploadFile(any(), anyString(), anyString());
        verify(postCrudRepository, never()).save(any());
    }

    @Test
    void testAddNewPost_ImageTooLarge_ThrowsBadRequestFileTooLarge() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        AddPostRequest request = AddPostRequest.builder()
                .caption("caption")
                .build();

        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getSize()).thenReturn(6L * 1024 * 1024); // 6MB

        when(userRepository.findByIdAndIsDeletedFalse(userId))
                .thenReturn(Optional.of(new UserEntity()));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            try {
                postService.addNewPost(userId, request, image);
            } catch (Exception e) {
                if (e instanceof BusinessException be) {
                    throw be;
                }
                fail("Unexpected exception type: " + e);
            }
        });
        assertEquals(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE.getMessage(), ex.getMessage());
        verify(fileStorageService, never()).uploadFile(any(), anyString(), anyString());
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
                .imageUrl("object/name.jpg")
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
                .imageUrl("object/name.jpg")
                .createdAt(LocalDateTime.now())
                .build();

        when(postCrudRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(Optional.of(post));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.deletePost(postId, UUID.randomUUID()));

        assertEquals(GlobalMessage.ACCESS_DENIED.getMessage(), exception.getMessage());
        verify(postCrudRepository, never()).save(any());
    }
}
