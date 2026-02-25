package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.dto.UserLoginDTO;
import com.rizkysiregar.feedzmore.model.dto.UserRegisterDto;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.RegisterUserRequest;
import com.rizkysiregar.feedzmore.model.request.ResetPasswordRequest;
import com.rizkysiregar.feedzmore.model.request.UserLoginRequest;
import com.rizkysiregar.feedzmore.model.request.UserSearchRequest;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import com.rizkysiregar.feedzmore.config.RedisUtil;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UsersServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JWTService jwtService;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private UsersService usersService;


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
        Operation result = usersService.register(request);

        // Assert
        assertNotNull(result);
        assertEquals(Operation.CREATED, result);
        verify(userRepository, times(1))
                .save(any(UserEntity.class));
    }


    @Test
    void testRegister_Failure_MissingUsernameOrPassword() {
        // Arrange
//        UserEntity userEntity = new UserEntity(); // no username or password set
        RegisterUserRequest registerUserRequest = new RegisterUserRequest();

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> usersService.register(registerUserRequest));
        assertEquals("Bad Request", exception.getMessage());
        verify(userRepository, never())
                .save(any(UserEntity.class));
    }


    @Test
    void testVerify_Success() {
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

        when(jwtService.generateToken(anyString())).thenReturn("mockedToken");

        // Act
        UserLoginDTO result = usersService.verify(loginRequest);


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
        Operation result = usersService.confirmResetPassword(request);

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
        BusinessException ex = assertThrows(BusinessException.class, () -> usersService.confirmResetPassword(request));
        assertEquals("Data Not Found", ex.getMessage());
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
        BusinessException ex = assertThrows(BusinessException.class, () -> usersService.confirmResetPassword(request));
        assertEquals("Bad Request, Invalid Reset Token", ex.getMessage());
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
        BusinessException ex = assertThrows(BusinessException.class, () -> usersService.confirmResetPassword(request));
        assertEquals("Bad Request, Invalid Reset Token", ex.getMessage());
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

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        // Act
        UserDetailDTO dto = usersService.getUserById(id.toString());

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
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> usersService.getUserById(id.toString()));
        // Service wraps NOT_FOUND into INTERNAL_ERROR due to broad catch(Exception)
        assertEquals("Internal Server Error", ex.getMessage());
    }

    @Test
    void testGetUserById_InvalidUUID() {
        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> usersService.getUserById("invalid-uuid"));
        assertEquals("Bad Request", ex.getMessage());
    }

    @Test
    void testSearch_Success() {
        // Arrange
        UserSearchRequest request = new UserSearchRequest("john", null);
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
        Page<UserDetailDTO> result = usersService.search(request, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("johnny", result.getContent().get(0).username());
    }

    @Test
    void testSearch_NotFound() {
        // Arrange
        UserSearchRequest request = new UserSearchRequest("someone", null);
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll((org.springframework.data.jpa.domain.Specification<UserEntity>) any(), eq(pageable))).thenReturn(Page.empty(pageable));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> usersService.search(request, pageable));
        assertEquals("Data Not Found", ex.getMessage());
    }

}
