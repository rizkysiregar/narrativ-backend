package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.GoogleUserInfo;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.response.LoginResponse;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.Role;
import com.rizkysiregar.feedzmore.model.enums.UserStatus;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.request.*;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.auth.GoogleTokenVerifierService;
import com.rizkysiregar.feedzmore.util.Operation;
import com.rizkysiregar.feedzmore.util.RedisUtil;
import com.rizkysiregar.feedzmore.util.UsernameGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JWTService jwtService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private UserSearchService userSearchService;

    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;

    @Mock
    private UsernameGenerator usernameGenerator;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuthService authService;


    @BeforeEach
    void setup() {
        // additional setup if needed
    }


    @Test
    void testRegister_Success() {
        // Arrange
        UserEntity userEntity = new UserEntity();
        userEntity.setUsername("testuser");
        userEntity.setPassword("password");

        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setEmail("test@mail.com");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setPhoneNumber("1234567890");
        request.setProfileImage("profile.png");

        when(userRepository.save(any(UserEntity.class)))
                .thenReturn(userEntity);

        // Act
        Operation result = authService.register(request, false);

        // Assert
        assertNotNull(result);
        assertEquals(Operation.CREATED, result);
        verify(userRepository, times(1))
                .save(any(UserEntity.class));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void testRegisterAdmin_RecordsAuditLog() {
        UUID newAdminId = UUID.randomUUID();
        UserEntity saved = new UserEntity();
        saved.setId(newAdminId);
        saved.setUsername("newadmin");
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);

        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername("newadmin");
        request.setPassword("password");
        request.setEmail("admin@mail.com");
        request.setFirstName("New");
        request.setLastName("Admin");
        request.setPhoneNumber("1234567890");

        Operation result = authService.register(request, true);

        assertEquals(Operation.CREATED, result);
        verify(auditLogService).record(
                eq(AdminAuditAction.ADMIN_REGISTER),
                eq(AuditTargetType.USER),
                eq(newAdminId.toString()),
                isNull(),
                any());
    }

    @Test
    void testLogin_Success() {
        // Arrange
        UserLoginRequest loginRequest = new UserLoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        UserEntity userEntity = new UserEntity();
        UUID userId = UUID.randomUUID();
        userEntity.setId(userId);
        userEntity.setUsername("testuser");

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(userEntity));

        when(jwtService.generateToken(anyString(), any(UUID.class), any(Role.class))).thenReturn("mockedToken");

        // Act
        LoginResponse result = authService.login(loginRequest);


        // Assert
        assertNotNull(result);
        assertEquals("mockedToken", result.getToken());
        assertEquals("testuser", result.getUsername());
        assertEquals(userId.toString(), result.getUserId());


        verify(authenticationManager).authenticate(any());
        verify(userRepository).findByUsername("testuser");
    }


    @Test
    void testConfirmResetPassword_Success() {
        // Arrange
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("user@example.com")
                .resetToken("token-123")
                .newPassword("newPass")
                .build();

        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        user.setPassword("old");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(redisUtil.get("reset_token:".concat("user@example.com"))).thenReturn("token-123");

        // Act
        Operation result = authService.confirmResetPassword(request);

        // Assert
        assertEquals(Operation.UPDATED, result);
        verify(userRepository).save(user);
    }

    @Test
    void testConfirmResetPassword_UserNotFound() {
        // Arrange
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("missing@example.com")
                .resetToken("token-123")
                .newPassword("newPass")
                .build();

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.confirmResetPassword(request));
        assertEquals("Ups, data yang Anda cari tidak ditemukan.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testConfirmResetPassword_TokenMissing() {
        // Arrange
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("user@example.com")
                .resetToken("token-123")
                .newPassword("newPass")
                .build();

        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(redisUtil.get("reset_token:".concat("user@example.com"))).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.confirmResetPassword(request));
        assertEquals("Tautan perubahan kata sandi sudah tidak valid atau pernah digunakan.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testConfirmResetPassword_TokenMismatch() {
        // Arrange
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("user@example.com")
                .resetToken("token-ABC")
                .newPassword("newPass")
                .build();

        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(redisUtil.get("reset_token:".concat("user@example.com"))).thenReturn("token-123");

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.confirmResetPassword(request));
        assertEquals("Tautan perubahan kata sandi sudah tidak valid atau pernah digunakan.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testGetUserById_Success() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("john");
        user.setEmail("john@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPhoneNumber("123");
        user.setProfileImage("img.png");

        when(userRepository.findActiveById(id)).thenReturn(Optional.of(user));

        // Act
        UserDetailDTO dto = authService.getUserById(id.toString());

        // Assert
        assertNotNull(dto);
        assertEquals(id.toString(), dto.id());
        assertEquals("john", dto.username());
        assertEquals("john@example.com", dto.email());
        assertEquals("John Doe", dto.fullName());
    }

    @Test
    void testGetUserById_NotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(userRepository.findActiveById(id)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.getUserById(id.toString()));
        assertEquals("Ups, data yang Anda cari tidak ditemukan.", ex.getMessage());
    }

    @Test
    void testGetUserById_InvalidUUID() {
        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.getUserById("invalid-uuid"));
        assertEquals("Maaf, ada kendala saat memproses data Anda. Periksa kembali input Anda.", ex.getMessage());
    }

    @Test
    void testSearch_Success() {
        // Arrange
        UserSearchRequest request = new UserSearchRequest("john");
        Pageable pageable = PageRequest.of(0, 10);

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("johnny");
        user.setEmail("johnny@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");

        PageImpl<UserEntity> page = new PageImpl<>(java.util.List.of(user), pageable, 1);
        when(userRepository.findAll((org.springframework.data.jpa.domain.Specification<UserEntity>) any(), eq(pageable))).thenReturn(page);

        // Act
        PaginatedResponse<UserDetailDTO> result = authService.search(request, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("johnny", result.getContent().get(0).username());
    }

    @Test
    void testSearch_NotFound() {
        // Arrange
        UserSearchRequest request = new UserSearchRequest("someone");
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll((org.springframework.data.jpa.domain.Specification<UserEntity>) any(), eq(pageable))).thenReturn(Page.empty(pageable));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.search(request, pageable));
        assertEquals("Ups, data yang Anda cari tidak ditemukan.", ex.getMessage());
    }

    @Test
    void testGoogleLogin_Success_ExistingUser() {
        // Arrange
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("valid-id-token");

        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
                .email("test@mail.com")
                .firstName("John")
                .lastName("Doe")
                .picture("profile.png")
                .googleId("google-id-123")
                .build();

        UserEntity existingUser = new UserEntity();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@mail.com");
        existingUser.setUsername("testuser");
        existingUser.setRole(Role.USER);
        existingUser.setGoogleId("google-id-123");

        when(googleTokenVerifierService.verifyToken("valid-id-token")).thenReturn(googleUserInfo);
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(existingUser);
        when(jwtService.generateToken(anyString(), any(), any(Role.class))).thenReturn("mock-jwt-token");

        // Act
        LoginResponse result = authService.googleLogin(request);

        // Assert
        assertNotNull(result);
        assertEquals("mock-jwt-token", result.getToken());
        assertEquals("testuser", result.getUsername());
        verify(userRepository).save(any(UserEntity.class));
        verify(userSearchService).indexUser(existingUser);
    }

    @Test
    void testGoogleLogin_Success_NewUser() {
        // Arrange
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("valid-id-token");

        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
                .email("new@mail.com")
                .firstName("New")
                .lastName("User")
                .picture("profile.png")
                .googleId("google-id-456")
                .build();

        UserEntity newUser = new UserEntity();
        newUser.setId(UUID.randomUUID());
        newUser.setEmail("new@mail.com");
        newUser.setUsername("newuser");
        newUser.setRole(Role.USER);

        when(googleTokenVerifierService.verifyToken("valid-id-token")).thenReturn(googleUserInfo);
        when(userRepository.findByEmail("new@mail.com")).thenReturn(Optional.empty());
        when(usernameGenerator.generateFromEmail("new@mail.com")).thenReturn("newuser");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity u = invocation.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateToken(anyString(), any(), any(Role.class))).thenReturn("mock-jwt-token");

        // Act
        LoginResponse result = authService.googleLogin(request);

        // Assert
        assertNotNull(result);
        assertEquals("mock-jwt-token", result.getToken());
        assertEquals("newuser", result.getUsername());
        verify(userRepository).save(any(UserEntity.class));
        verify(userSearchService).indexUser(any(UserEntity.class));
    }

    @Test
    void testRefreshToken_Success_RotatesToken() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        user.setRole(Role.USER);

        when(redisUtil.get("refresh_token:old-token")).thenReturn(userId.toString());
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(jwtService.generateToken("testuser", userId, Role.USER)).thenReturn("newAccess");

        LoginResponse result = authService.refreshToken("old-token");

        assertEquals("newAccess", result.getToken());
        assertNotNull(result.getRefreshToken());
        assertNotEquals("old-token", result.getRefreshToken());
        assertEquals(userId.toString(), result.getUserId());
        verify(redisUtil).delete("refresh_token:old-token");
        verify(redisUtil).removeFromSet("user_refresh_tokens:" + userId, "old-token");
        verify(redisUtil).set(eq("refresh_token:" + result.getRefreshToken()), eq(userId.toString()), anyLong());
        verify(redisUtil).addToSet(eq("user_refresh_tokens:" + userId), eq(result.getRefreshToken()), anyLong());
    }

    @Test
    void testRefreshToken_InvalidToken() {
        when(redisUtil.get("refresh_token:bad")).thenReturn(null);

        assertThrows(BusinessException.class, () -> authService.refreshToken("bad"));
        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    void testRefreshToken_UserDeleted() {
        UUID userId = UUID.randomUUID();
        when(redisUtil.get("refresh_token:tok")).thenReturn(userId.toString());
        when(userRepository.findActiveById(userId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> authService.refreshToken("tok"));
        verify(redisUtil).delete("refresh_token:tok");
    }


    @Test
    void testRevokeRefreshTokens_DeletesEveryIndexedTokenAndTheIndex() {
        UUID userId = UUID.randomUUID();
        when(redisUtil.membersOfSet("user_refresh_tokens:" + userId)).thenReturn(java.util.Set.of("t1", "t2"));

        authService.revokeRefreshTokens(userId);

        verify(redisUtil).delete("refresh_token:t1");
        verify(redisUtil).delete("refresh_token:t2");
        verify(redisUtil).delete("user_refresh_tokens:" + userId);
    }

    @Test
    void testLogin_BannedUser_IsRejectedAfterPasswordCheckAndIssuesNothing() {
        UserLoginRequest loginRequest = new UserLoginRequest();
        loginRequest.setUsername("banned");
        loginRequest.setPassword("password");
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        UserEntity banned = new UserEntity();
        banned.setId(UUID.randomUUID());
        banned.setUsername("banned");
        banned.setStatus(UserStatus.BANNED);
        when(userRepository.findByUsername("banned")).thenReturn(Optional.of(banned));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(loginRequest));

        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
        assertEquals(GlobalMessage.USER_BANNED.getMessage(), exception.getMessage());
        verifyNoInteractions(jwtService, redisUtil);
    }

    @Test
    void testGoogleLogin_BannedUser_IsRejectedBeforeAnythingIsSavedOrIndexed() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("id-token");
        GoogleUserInfo info = GoogleUserInfo.builder().email("banned@mail.com").googleId("g-1").build();
        when(googleTokenVerifierService.verifyToken("id-token")).thenReturn(info);

        UserEntity banned = new UserEntity();
        banned.setId(UUID.randomUUID());
        banned.setGoogleId("g-1");
        banned.setStatus(UserStatus.BANNED);
        when(userRepository.findByEmail("banned@mail.com")).thenReturn(Optional.of(banned));

        assertThrows(BusinessException.class, () -> authService.googleLogin(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userSearchService, jwtService);
    }
}
