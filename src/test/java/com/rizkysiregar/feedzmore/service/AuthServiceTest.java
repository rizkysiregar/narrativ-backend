package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.repository.UserBlockRepository;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import org.springframework.http.HttpStatus;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rizkysiregar.feedzmore.model.dto.PublicUserDTO;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import com.rizkysiregar.feedzmore.service.otp.IRegistrationOtpService;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

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
    private GoogleTokenVerifierService googleTokenVerifierService;

    @Mock
    private UsernameGenerator usernameGenerator;

    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private ProfileImageUrlResolver profileImageUrlResolver;
    @Mock
    private IRegistrationOtpService registrationOtpService;

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

    private RegisterUserRequest registerRequest(String token) {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setEmail("test@mail.com");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setRegistrationToken(token);
        return request;
    }

    @Test
    void testRegister_VerificationOn_ConsumesTheTokenBeforeSaving() {
        when(registrationOtpService.isVerificationRequired()).thenReturn(true);
        when(userRepository.save(any(UserEntity.class))).thenReturn(new UserEntity());

        assertEquals(Operation.CREATED, authService.register(registerRequest("token-1"), false));

        var order = inOrder(registrationOtpService, userRepository);
        order.verify(registrationOtpService).consumeRegistrationToken("test@mail.com", "token-1");
        order.verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void testRegister_VerificationOn_BadTokenCreatesNoUser() {
        when(registrationOtpService.isVerificationRequired()).thenReturn(true);
        doThrow(new BusinessException(GlobalMessage.EMAIL_NOT_VERIFIED, HttpStatus.BAD_REQUEST))
                .when(registrationOtpService).consumeRegistrationToken("test@mail.com", null);

        assertThrows(BusinessException.class, () -> authService.register(registerRequest(null), false));

        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegister_VerificationOn_TakenUsernameKeepsTheToken() {
        lenient().when(registrationOtpService.isVerificationRequired()).thenReturn(true);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(new UserEntity()));

        assertThrows(BusinessException.class, () -> authService.register(registerRequest("token-1"), false));

        // the user can fix the username and register again with the same verified email
        verify(registrationOtpService, never()).consumeRegistrationToken(any(), any());
    }

    @Test
    void testRegister_VerificationOff_NeedsNoToken() {
        when(registrationOtpService.isVerificationRequired()).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(new UserEntity());

        assertEquals(Operation.CREATED, authService.register(registerRequest(null), false));

        verify(registrationOtpService, never()).consumeRegistrationToken(any(), any());
    }

    @Test
    void testRegisterAdmin_NeedsNoToken() {
        lenient().when(registrationOtpService.isVerificationRequired()).thenReturn(true);
        UserEntity saved = new UserEntity();
        saved.setId(UUID.randomUUID());
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);

        authService.register(registerRequest(null), true);

        verify(registrationOtpService, never()).consumeRegistrationToken(any(), any());
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


    private static final String INVALID_RESET_TOKEN_MESSAGE =
            "Tautan perubahan kata sandi sudah tidak valid atau pernah digunakan.";

    private ResetPasswordRequest resetRequest(String email, String token) {
        return ResetPasswordRequest.builder()
                .email(email)
                .resetToken(token)
                .newPassword("newPass")
                .build();
    }

    @Test
    void testConfirmResetPassword_Success() {
        // Arrange
        ResetPasswordRequest request = resetRequest("user@example.com", "token-123");

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setPassword("old");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(redisUtil.get("reset_token:".concat("user@example.com"))).thenReturn("token-123");

        // Act
        Operation result = authService.confirmResetPassword(request);

        // Assert
        assertEquals(Operation.UPDATED, result);
        assertNotEquals("old", user.getPassword());
        verify(userRepository).save(user);
        // one time use
        verify(redisUtil).delete("reset_token:user@example.com");
        // every session of the account is logged out
        verify(redisUtil).membersOfSet("user_refresh_tokens:" + userId);
        verify(redisUtil).delete("user_refresh_tokens:" + userId);
    }

    @Test
    void testConfirmResetPassword_UnknownEmailLooksLikeABadToken() {
        // no token was ever issued for this email, and nothing reveals whether the email is registered
        ResetPasswordRequest request = resetRequest("missing@example.com", "token-123");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.confirmResetPassword(request));
        assertEquals(INVALID_RESET_TOKEN_MESSAGE, ex.getMessage());
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testConfirmResetPassword_TokenMissing() {
        ResetPasswordRequest request = resetRequest("user@example.com", "token-123");
        when(redisUtil.get("reset_token:".concat("user@example.com"))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.confirmResetPassword(request));
        assertEquals(INVALID_RESET_TOKEN_MESSAGE, ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testConfirmResetPassword_TokenMismatchKeepsTheRealToken() {
        ResetPasswordRequest request = resetRequest("user@example.com", "token-ABC");
        when(redisUtil.get("reset_token:".concat("user@example.com"))).thenReturn("token-123");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.confirmResetPassword(request));
        assertEquals(INVALID_RESET_TOKEN_MESSAGE, ex.getMessage());
        verify(userRepository, never()).save(any());
        // a wrong guess must not burn the token of the real owner
        verify(redisUtil, never()).delete("reset_token:user@example.com");
    }

    @Test
    void testConfirmResetPassword_DeletedAccountCannotBeReset() {
        ResetPasswordRequest request = resetRequest("gone@example.com", "token-123");
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("gone@example.com");
        user.setDeleted(true);
        when(redisUtil.get("reset_token:gone@example.com")).thenReturn("token-123");
        when(userRepository.findByEmail("gone@example.com")).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.confirmResetPassword(request));
        assertEquals(INVALID_RESET_TOKEN_MESSAGE, ex.getMessage());
        verify(userRepository, never()).save(any());
        verify(redisUtil).delete("reset_token:gone@example.com");
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
        when(profileImageUrlResolver.resolve("img.png")).thenReturn("https://cdn/img.png");

        // Act
        PublicUserDTO dto = authService.getUserById(id.toString(), UUID.randomUUID());

        // Assert
        assertNotNull(dto);
        assertEquals(id.toString(), dto.id());
        assertEquals("john", dto.username());
        assertEquals("John Doe", dto.fullName());
        assertEquals("https://cdn/img.png", dto.profileImageUrl());
    }

    @Test
    void testGetUserById_NotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(userRepository.findActiveById(id)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.getUserById(id.toString(), UUID.randomUUID()));
        assertEquals("Ups, data yang Anda cari tidak ditemukan.", ex.getMessage());
    }

    @Test
    void testGetUserById_InvalidUUID() {
        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.getUserById("invalid-uuid", UUID.randomUUID()));
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
        PaginatedResponse<PublicUserDTO> result = authService.search(request, UUID.randomUUID(), pageable);

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
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.search(request, UUID.randomUUID(), pageable));
        assertEquals("Ups, data yang Anda cari tidak ditemukan.", ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSearch_matchesPercentAndUnderscoreLiterally() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll((org.springframework.data.jpa.domain.Specification<UserEntity>) any(), eq(pageable)))
                .thenReturn(Page.empty(pageable));
        assertThrows(BusinessException.class, () -> authService.search(new UserSearchRequest("50%_Off"), UUID.randomUUID(), pageable));

        ArgumentCaptor<org.springframework.data.jpa.domain.Specification<UserEntity>> captor =
                ArgumentCaptor.forClass(org.springframework.data.jpa.domain.Specification.class);
        verify(userRepository).findAll(captor.capture(), eq(pageable));
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        Root<UserEntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);

        verify(cb).like(ArgumentMatchers.<Expression<String>>any(), eq("%50\\%\\_off%"), eq('\\'));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSearch_withoutAUsernameDoesNotFilterOnIt() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll((org.springframework.data.jpa.domain.Specification<UserEntity>) any(), eq(pageable)))
                .thenReturn(Page.empty(pageable));
        assertThrows(BusinessException.class, () -> authService.search(new UserSearchRequest("  "), UUID.randomUUID(), pageable));

        ArgumentCaptor<org.springframework.data.jpa.domain.Specification<UserEntity>> captor =
                ArgumentCaptor.forClass(org.springframework.data.jpa.domain.Specification.class);
        verify(userRepository).findAll(captor.capture(), eq(pageable));
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        Root<UserEntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);

        verify(cb, never()).like(ArgumentMatchers.<Expression<String>>any(), anyString(), anyChar());
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
    }

    @Test
    void testRefreshToken_Success_RotatesToken() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        user.setRole(Role.USER);

        when(redisUtil.get("refresh_token:old-token")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
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
    void logoutRevokesOnlyTheGivenRefreshToken() {
        UUID userId = UUID.randomUUID();
        when(redisUtil.get("refresh_token:tok")).thenReturn(userId.toString());

        authService.logout("tok");

        verify(redisUtil).delete("refresh_token:tok");
        verify(redisUtil).removeFromSet("user_refresh_tokens:" + userId, "tok");
    }

    @Test
    void logoutWithAnUnknownTokenIsANoOp() {
        when(redisUtil.get("refresh_token:gone")).thenReturn(null);

        authService.logout("gone");

        verify(redisUtil).delete("refresh_token:gone");
        verify(redisUtil, never()).removeFromSet(anyString(), anyString());
    }

    @Test
    void confirmsIdentityWithThePassword() {
        UserEntity user = new UserEntity();
        user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("secret"));

        assertTrue(authService.confirmsIdentity(user, "secret", null));
        assertFalse(authService.confirmsIdentity(user, "wrong", null));
        assertFalse(authService.confirmsIdentity(user, null, null));
        assertFalse(authService.confirmsIdentity(user, " ", " "));
    }

    @Test
    void confirmsIdentityWithTheLinkedGoogleAccountOnly() {
        UserEntity user = new UserEntity();
        user.setPassword("not-a-hash");
        user.setGoogleId("g-1");
        GoogleUserInfo mine = GoogleUserInfo.builder().googleId("g-1").build();
        GoogleUserInfo other = GoogleUserInfo.builder().googleId("g-2").build();
        when(googleTokenVerifierService.verifyToken("mine")).thenReturn(mine);
        when(googleTokenVerifierService.verifyToken("other")).thenReturn(other);
        when(googleTokenVerifierService.verifyToken("bad"))
                .thenThrow(new BusinessException(GlobalMessage.GOOGLE_UNAUTHORIZED, HttpStatus.UNAUTHORIZED));

        assertTrue(authService.confirmsIdentity(user, null, "mine"));
        assertFalse(authService.confirmsIdentity(user, null, "other"));
        assertFalse(authService.confirmsIdentity(user, null, "bad"));

        UserEntity noGoogle = new UserEntity();
        noGoogle.setPassword("not-a-hash");
        assertFalse(authService.confirmsIdentity(noGoogle, null, "mine"));
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
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> authService.refreshToken("tok"));
        verify(redisUtil).delete("refresh_token:tok");
    }


    @Test
    void testRefreshToken_DeletedUser_DropsTheTokenAndIsUnauthorized() {
        UUID userId = UUID.randomUUID();
        UserEntity deleted = new UserEntity();
        deleted.setId(userId);
        deleted.setDeleted(true);
        when(redisUtil.get("refresh_token:tok")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(deleted));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken("tok"));

        assertEquals(GlobalMessage.UNAUTHORIZED.getMessage(), exception.getMessage());
        verify(redisUtil).delete("refresh_token:tok");
    }

    @Test
    void testRefreshToken_BannedUser_GetsTheBanReasonAndKeepsTheTokenSoEveryRetryExplainsIt() {
        UUID userId = UUID.randomUUID();
        UserEntity banned = new UserEntity();
        banned.setId(userId);
        banned.setStatus(UserStatus.BANNED);
        banned.setBanReason("posting spam");
        when(redisUtil.get("refresh_token:tok")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(banned));

        for (int attempt = 0; attempt < 2; attempt++) {
            BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken("tok"));
            assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
            assertEquals("Akun Anda telah diblokir oleh admin. Alasan: posting spam", exception.getMessage());
        }

        verify(redisUtil, never()).delete(anyString());
        verifyNoInteractions(jwtService);
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
        banned.setBanReason("posting spam");
        when(userRepository.findByUsername("banned")).thenReturn(Optional.of(banned));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(loginRequest));

        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
        assertEquals("Akun Anda telah diblokir oleh admin. Alasan: posting spam", exception.getMessage());
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
        verifyNoInteractions(jwtService);
    }
}
