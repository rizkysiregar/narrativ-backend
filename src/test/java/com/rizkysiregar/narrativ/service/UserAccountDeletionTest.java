package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.Role;
import com.rizkysiregar.narrativ.model.request.DeleteAccountRequest;
import com.rizkysiregar.narrativ.repository.CommentRepository;
import com.rizkysiregar.narrativ.repository.FollowRepository;
import com.rizkysiregar.narrativ.repository.LikeRepository;
import com.rizkysiregar.narrativ.repository.PostCrudRepository;
import com.rizkysiregar.narrativ.repository.PostMediaRepository;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.util.Operation;
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
    private MediaCleanupService mediaCleanupService;
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
        verifyNoInteractions(userDeviceService, profileImageService, mediaCleanupService);
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

        when(postMediaRepository.findMediaKeysOfLivePosts(userId)).thenReturn(List.of("u/POST_FEED/p.mp4"));
        when(postMediaRepository.findThumbnailKeysOfLivePosts(userId)).thenReturn(List.of("u/POST_FEED/t.jpg"));

        assertEquals(Operation.DELETED, service.deleteMyAccount(userId, new DeleteAccountRequest("pw", null)));

        InOrder order = inOrder(authService, userDeviceService, profileImageService, userRepository);
        order.verify(authService).revokeRefreshTokens(userId);
        order.verify(userDeviceService).removeAllDevices(userId);
        order.verify(profileImageService).deleteProfileImage(userId);
        order.verify(userRepository).save(user);

        // rows first, then the files in the background: a file left behind is an orphan, not a linked leftover
        InOrder posts = inOrder(postMediaRepository, postCrudRepository, mediaCleanupService);
        posts.verify(postMediaRepository).deleteAllOfLivePosts(userId);
        posts.verify(postCrudRepository).softDeleteAllByUserId(userId);
        posts.verify(mediaCleanupService).deleteObjects(List.of("u/POST_FEED/p.mp4", "u/POST_FEED/t.jpg"));
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
    void anAccountWithoutPostsStartsNoCleanup() {
        UserEntity user = user(Role.USER);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(authService.confirmsIdentity(user, "pw", null)).thenReturn(true);

        service.deleteMyAccount(userId, new DeleteAccountRequest("pw", null));

        verifyNoInteractions(mediaCleanupService);
        verify(postCrudRepository).softDeleteAllByUserId(userId);
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
