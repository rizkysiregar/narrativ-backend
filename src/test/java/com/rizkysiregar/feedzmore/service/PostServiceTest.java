package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.messaging.publisher.PostEventPublisher;
import com.rizkysiregar.feedzmore.model.dto.AddPostDTO;
import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.*;
import com.rizkysiregar.feedzmore.service.impl.PostServiceImpl;
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
        PostDTO p1 = new PostDTO(UUID.randomUUID(), "alice", "hello", "img1.jpg", LocalDateTime.now(), 3, 1, true);
        PostDTO p2 = new PostDTO(UUID.randomUUID(), "bob", "world", "img2.jpg", LocalDateTime.now().minusMinutes(1), 0, 0, false);
        List<PostDTO> content = List.of(p1, p2);
        Page<PostDTO> postPage = new PageImpl<>(content, PageRequest.of(page, size), content.size());

        when(postPaginationRepository.findAllPost(any(Pageable.class), eq(userId)))
                .thenReturn(postPage);

        try {
            when(fileStorageService.getPresignUrl("img1.jpg")).thenReturn("img1.jpg");
            when(fileStorageService.getPresignUrl("img2.jpg")).thenReturn("img2.jpg");
        } catch (Exception e) {
            // do nothing
        }

        // Act
        PaginatedResponse<PostDTO> response = postService.getAllPost(page, size, userId);

        // Assert
        assertNotNull(response);
        assertEquals(page, response.getPageNumber());
        assertEquals(size, response.getPageSize());
        assertEquals(content.size(), response.getContent().size());
        assertEquals(content.size(), response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        // image url should be kept as is
        assertEquals("img1.jpg", response.getContent().get(0).getImageUrl());
        assertEquals("img2.jpg", response.getContent().get(1).getImageUrl());

        verify(postPaginationRepository, times(1))
                .findAllPost(any(Pageable.class), eq(userId));
    }

    @Test
    void testAddNewPost_Success() throws Exception {
        // Arrange
        AddPostRequest request = AddPostRequest.builder()
                .username("testuser")
                .caption("My first post")
                .build();

        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getSize()).thenReturn(1024L); // 1KB

        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setEmail("test@mail.com");

        when(userRepository.findByUsername("testuser"))
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
        AddPostDTO result = postService.addNewPost(request, image);

        // Assert
        assertNotNull(result);
        assertEquals(GlobalMessage.CREATED.getMessage(), result.getStatus());
        verify(fileStorageService, times(1))
                .uploadFile(eq(image), eq("testuser"), anyString());
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

        PostDTO p1 = new PostDTO(UUID.randomUUID(), username, "hello", "img1.jpg", LocalDateTime.now(), 3, 1, true);
        List<PostDTO> content = List.of(p1);
        Page<PostDTO> postPage = new PageImpl<>(content, PageRequest.of(page, size), content.size());

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(postPaginationRepository.findAllPostByUserId(any(Pageable.class), eq(userId)))
                .thenReturn(postPage);

        // Act
        PaginatedResponse<PostDTO> response = postService.getAllPostByUserId(page, size, username);

        // Assert
        assertNotNull(response);
        assertEquals(username, response.getContent().get(0).getUsername());
        verify(userRepository).findByUsername(username);
        verify(postPaginationRepository).findAllPostByUserId(any(Pageable.class), eq(userId));
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
        AddPostRequest request = AddPostRequest.builder()
                .username("nouser")
                .caption("caption")
                .build();
        MultipartFile image = mock(MultipartFile.class);

        when(userRepository.findByUsername("nouser"))
                .thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            try {
                postService.addNewPost(request, image);
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
        AddPostRequest request = AddPostRequest.builder()
                .username("testuser")
                .caption("caption")
                .build();

        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(true);

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(new UserEntity()));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            try {
                postService.addNewPost(request, image);
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
        AddPostRequest request = AddPostRequest.builder()
                .username("testuser")
                .caption("caption")
                .build();

        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getSize()).thenReturn(6L * 1024 * 1024); // 6MB

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(new UserEntity()));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            try {
                postService.addNewPost(request, image);
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
}
