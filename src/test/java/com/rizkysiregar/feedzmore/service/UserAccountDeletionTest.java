package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.PostMediaEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.Role;
import com.rizkysiregar.feedzmore.model.request.DeleteAccountRequest;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.FollowRepository;
import com.rizkysiregar.feedzmore.repository.LikeRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.PostMediaRepository;
import com.rizkysiregar.feedzmore.repository.UserBlockRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountDeletionTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthService authService;
    @Mock
    private PostCrudRepository postCrudRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private PostMediaRepository postMediaRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private UserDeviceService userDeviceService;
    @Mock
    private ProfileImageService profileImageService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ReportClosingService reportClosingService;
    @Mock
    private ProfileImageUrlResolver profileImageUrlResolver;

    @InjectMocks
    private UserManagementService service;

    private final UUID userId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private UserEntity user(Role role) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("someone");
        user.setEmail("someone@mail.com");
        user.setFirstName("Some");
        user.setLastName("One");
        user.setPhoneNumber("0812345678");
        user.setGoogleId("google-1");
        user.setProfileImage("u/PROFILE_PICTURE/a.jpg");
        user.setRole(role);
        return user;
    }

    @Test
    void deleteWithWrongProofChangesNothing() {
        UserEntity user = user(Role.USER);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(authService.confirmsIdentity(user, "wrong", null)).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteMyAccount(userId, new DeleteAccountRequest("wrong", null)));

        assertEquals(GlobalMessage.CONFIRMATION_FAILED.getMessage(), e.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, e.getHttpStatus());
        assertFalse(user.isDeleted());
        verify(userRepository, never()).save(user);
        verifyNoInteractions(userDeviceService, profileImageService, fileStorageService);
        verify(authService, never()).revokeRefreshTokens(userId);
    }

    @Test
    void adminAccountsCannotBeDeletedFromTheApp() {
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user(Role.ADMIN)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteMyAccount(userId, new DeleteAccountRequest("pw", null)));

        assertEquals(GlobalMessage.CANNOT_DELETE_ADMIN.getMessage(), e.getMessage());
        verifyNoInteractions(userDeviceService);
    }

    @Test
    void deleteCutsSessionsFirstThenRemovesContentAndAnonymizesTheAccount() {
        UserEntity user = user(Role.USER);
        String oldPassword = user.getPassword();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(authService.confirmsIdentity(user, "pw", null)).thenReturn(true);
        when(authService.unusablePasswordHash()).thenReturn("random-hash");

        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(user).build();
        PostMediaEntity media = PostMediaEntity.builder().mediaKey("u/POST_FEED/p.mp4").thumbnailKey("u/POST_FEED/t.jpg").build();
        when(postCrudRepository.findLiveByUserId(userId)).thenReturn(List.of(post));
        when(postMediaRepository.findByPostIdOrderByPositionAsc(post.getId())).thenReturn(List.of(media));

        assertEquals(Operation.DELETED, service.deleteMyAccount(userId, new DeleteAccountRequest("pw", null)));

        InOrder order = inOrder(authService, userDeviceService, profileImageService, userRepository);
        order.verify(authService).revokeRefreshTokens(userId);
        order.verify(userDeviceService).removeAllDevices(userId);
        order.verify(profileImageService).deleteProfileImage(userId);
        order.verify(userRepository).save(user);

        assertTrue(post.isDeleted());
        verify(postCrudRepository).save(post);
        verify(fileStorageService).deleteObject("u/POST_FEED/p.mp4");
        verify(fileStorageService).deleteObject("u/POST_FEED/t.jpg");
        verify(commentRepository).overwriteContentByUserId(userId, "[deleted]");
        verify(likeRepository).deleteAllByUserId(userId);
        verify(followRepository).deleteAllByUserId(userId);
        verify(userBlockRepository).deleteAllByUserId(userId);

        assertTrue(user.isDeleted());
        assertEquals("del_111111112222", user.getUsername());
        assertEquals("deleted-11111111222233334444555555555555@deleted.invalid", user.getEmail());
        assertEquals("Deleted", user.getFirstName());
        assertNull(user.getPhoneNumber());
        assertNull(user.getGoogleId());
        assertNull(user.getProfileImage());
        assertEquals("random-hash", user.getPassword());
        assertFalse(oldPassword != null && oldPassword.equals(user.getPassword()));
    }

    @Test
    void usernameFitsTheColumn() {
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity user = user(Role.USER);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(authService.confirmsIdentity(user, null, "id-token")).thenReturn(true);

        service.deleteMyAccount(userId, new DeleteAccountRequest(null, "id-token"));

        verify(userRepository).save(saved.capture());
        assertTrue(saved.getValue().getUsername().length() <= 16);
    }
}
