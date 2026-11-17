package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.dto.GoogleUserInfo;
import com.rizkysiregar.narrativ.model.enums.AdminAuditAction;
import com.rizkysiregar.narrativ.model.enums.AuditTargetType;
import com.rizkysiregar.narrativ.model.enums.Role;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.model.request.*;
import com.rizkysiregar.narrativ.service.auth.GoogleTokenVerifierService;
import com.rizkysiregar.narrativ.service.otp.IRegistrationOtpService;
import com.rizkysiregar.narrativ.util.RedisUtil;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.model.dto.PublicUserDTO;
import com.rizkysiregar.narrativ.model.response.LoginResponse;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.util.BanMessages;
import com.rizkysiregar.narrativ.util.EmailMasking;
import com.rizkysiregar.narrativ.util.LikePatterns;
import com.rizkysiregar.narrativ.util.Operation;
import com.rizkysiregar.narrativ.util.UsernameGenerator;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    // set of the refresh tokens a user currently holds, so they can all be revoked at once (ban, force logout)
    private static final String USER_REFRESH_TOKENS_PREFIX = "user_refresh_tokens:";
    private static final long REFRESH_TOKEN_TTL_SECONDS = 60L * 60 * 24 * 30; // 30 days

    final UserRepository userRepository;
    final AuthenticationManager authManager;
    final JWTService jwtService;
    private final RedisUtil redisUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final UsernameGenerator usernameGenerator;
    private final AuditLogService auditLogService;
    private final ProfileImageUrlResolver profileImageUrlResolver;
    private final UserBlockRepository userBlockRepository;
    private final IRegistrationOtpService registrationOtpService;

    public AuthService(UserRepository userRepository,
                       AuthenticationManager authManager,
                       JWTService jwtService,
                       RedisUtil redisUtil,
                       GoogleTokenVerifierService googleTokenVerifierService,
                       UsernameGenerator usernameGenerator,
                       AuditLogService auditLogService,
                       ProfileImageUrlResolver profileImageUrlResolver,
                       UserBlockRepository userBlockRepository,
                       IRegistrationOtpService registrationOtpService
    ) {
        this.userRepository = userRepository;
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.redisUtil = redisUtil;
        this.googleTokenVerifierService = googleTokenVerifierService;
        this.usernameGenerator = usernameGenerator;
        this.auditLogService = auditLogService;
        this.profileImageUrlResolver = profileImageUrlResolver;
        this.userBlockRepository = userBlockRepository;
        this.registrationOtpService = registrationOtpService;
    }

    @Transactional
    public Operation register(RegisterUserRequest registerUserRequest, Boolean isAdmin) {

        if (userRepository.findByUsername(registerUserRequest.getUsername()).isPresent()) {
            throw new BusinessException(GlobalMessage.USERNAME_ALREADY_TAKEN, HttpStatus.BAD_REQUEST);
        }

        if (userRepository.findByEmail(registerUserRequest.getEmail()).isPresent()) {
            throw new BusinessException(GlobalMessage.EMAIL_ALREADY_TAKEN, HttpStatus.BAD_REQUEST);
        }

        // admins are created by an admin, only self-registration has to prove the mailbox
        if (!isAdmin && registrationOtpService.isVerificationRequired()) {
            registrationOtpService.consumeRegistrationToken(registerUserRequest.getEmail(),
                    registerUserRequest.getRegistrationToken());
        }

        Role role = Role.USER;

        if (isAdmin) {
            log.debug("AUTH.REGISTER.ROLE role=ADMIN");
            role = Role.ADMIN;
        }

        UserEntity entity = new UserEntity();
        entity.setUsername(registerUserRequest.getUsername());
        entity.setPassword(encoder.encode(registerUserRequest.getPassword()));
        entity.setEmail(registerUserRequest.getEmail());
        entity.setFirstName(registerUserRequest.getFirstName());
        entity.setLastName(registerUserRequest.getLastName());
        entity.setProfileImage(registerUserRequest.getProfileImage());
        entity.setRole(role);

        UserEntity saved = userRepository.save(entity);

        if (isAdmin) {
            auditLogService.record(AdminAuditAction.ADMIN_REGISTER, AuditTargetType.USER,
                    saved.getId().toString(), null,
                    AuditLogService.metadata("username", entity.getUsername()));
        }
        return Operation.CREATED;
    }

    public LoginResponse login(UserLoginRequest userLoginRequest) {
        Authentication authentication =
                authManager.authenticate(new UsernamePasswordAuthenticationToken(userLoginRequest.getUsername(), userLoginRequest.getPassword()));


        if (!authentication.isAuthenticated()) {
            throw new BadCredentialsException("Bad Credentials!");
        }

        Optional<UserEntity> userEntity = userRepository.findByUsername(userLoginRequest.getUsername());

        if (userEntity.isEmpty()) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        // after the password was verified, so the ban status is never revealed to someone guessing credentials
        assertNotBanned(userEntity.get());

        String token = jwtService.generateToken(
                userEntity.get().getUsername(),
                userEntity.get().getId(),
                userEntity.get().getRole()
        );

        return LoginResponse.builder()
                .token(token)
                .refreshToken(issueRefreshToken(userEntity.get().getId()))
                .username(userEntity.get().getUsername())
                .userId(userEntity.get().getId().toString())
                .role(userEntity.get().getRole())
                .build();
    }

    public LoginResponse adminLogin(UserLoginRequest request) {
        LoginResponse loginResult = login(request);

        if (!loginResult.getRole().equals(Role.ADMIN)) {
            throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        return loginResult;
    }

    public LoginResponse googleLogin(GoogleLoginRequest request) {
        log.debug("AUTH.LOGIN.GOOGLE");
        // Verify idToken with Google and extract user info from it
        GoogleUserInfo googleUser = googleTokenVerifierService.verifyToken(request.getIdToken());

        // find existing user by email or create a new one
        UserEntity user = userRepository.findByEmail(googleUser.getEmail())
                .map(existing -> {
                    if (existing.getGoogleId() == null) {
                        existing.setGoogleId(googleUser.getGoogleId());
                        return userRepository.save(existing);
                    }

                    return existing;
                })
                .orElseGet(() -> createGoogleUser(googleUser));

        assertNotBanned(user);
        userRepository.save(user);
        // Generate JWT - same as regular login
        String token = jwtService.generateToken(user.getUsername(), user.getId(), user.getRole());
        return LoginResponse.builder()
                .token(token)
                .refreshToken(issueRefreshToken(user.getId()))
                .username(user.getUsername())
                .userId(user.getId().toString())
                .role(user.getRole())
                .build();
    }

    public LoginResponse refreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX.concat(refreshToken);
        Object storedUserId = redisUtil.get(key);

        if (storedUserId == null) {
            throw new BusinessException(GlobalMessage.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        UserEntity user;
        try {
            user = userRepository.findById(UUID.fromString(storedUserId.toString()))
                    .filter(found -> !found.isDeleted())
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            user = null;
        }

        if (user == null) {
            // the token belongs to nobody (anymore): it is useless, drop it
            redisUtil.delete(key);
            throw new BusinessException(GlobalMessage.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        // a banned user keeps the token on purpose: every retry must tell the app the account is banned, and why.
        // The tokens are revoked on unban, so a session from before the ban never silently comes back.
        assertNotBanned(user);

        // rotation: a refresh token can only be used once
        redisUtil.delete(key);
        redisUtil.removeFromSet(USER_REFRESH_TOKENS_PREFIX.concat(storedUserId.toString()), refreshToken);

        return LoginResponse.builder()
                .token(jwtService.generateToken(user.getUsername(), user.getId(), user.getRole()))
                .refreshToken(issueRefreshToken(user.getId()))
                .username(user.getUsername())
                .userId(user.getId().toString())
                .role(user.getRole())
                .build();
    }

    /**
     * Ends one session: the refresh token stops working at once. Unknown or already used tokens are ignored, so
     * a retry (or a logout after the session already expired) never fails.
     */
    public void logout(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX.concat(refreshToken);
        Object storedUserId = redisUtil.get(key);
        redisUtil.delete(key);
        if (storedUserId != null) {
            redisUtil.removeFromSet(USER_REFRESH_TOKENS_PREFIX.concat(storedUserId.toString()), refreshToken);
        }
    }

    /**
     * Whether the caller proved they own the account, for destructive actions a stolen access token must not be
     * enough for: the current password, or a fresh Google id token of the Google account linked to the user.
     */
    public boolean confirmsIdentity(UserEntity user, String password, String idToken) {
        if (password != null && !password.isBlank() && encoder.matches(password, user.getPassword())) {
            return true;
        }
        if (idToken != null && !idToken.isBlank() && user.getGoogleId() != null) {
            try {
                return user.getGoogleId().equals(googleTokenVerifierService.verifyToken(idToken).getGoogleId());
            } catch (BusinessException e) {
                return false;
            }
        }
        return false;
    }

    /** A password nobody knows, for accounts that no longer sign in. */
    public String unusablePasswordHash() {
        return encoder.encode(UUID.randomUUID().toString());
    }

    private String issueRefreshToken(UUID userId) {
        String refreshToken = UUID.randomUUID().toString();
        redisUtil.set(REFRESH_TOKEN_PREFIX.concat(refreshToken), userId.toString(), REFRESH_TOKEN_TTL_SECONDS);
        redisUtil.addToSet(USER_REFRESH_TOKENS_PREFIX.concat(userId.toString()), refreshToken, REFRESH_TOKEN_TTL_SECONDS);
        return refreshToken;
    }

    /**
     * Revokes every refresh token the user holds (force logout, unban). Access tokens stay valid until they
     * expire (15 minutes), tokens issued before the per-user index existed are not covered and die on their
     * own TTL or on use.
     */
    public void revokeRefreshTokens(UUID userId) {
        String indexKey = USER_REFRESH_TOKENS_PREFIX.concat(userId.toString());
        for (String token : redisUtil.membersOfSet(indexKey)) {
            redisUtil.delete(REFRESH_TOKEN_PREFIX.concat(token));
        }
        redisUtil.delete(indexKey);
    }

    private void assertNotBanned(UserEntity user) {
        if (user.getStatus() == UserStatus.BANNED) {
            throw BanMessages.bannedException(user.getBanReason());
        }
    }

    private UserEntity createGoogleUser(GoogleUserInfo googleUser) {
        UserEntity entity = new UserEntity();
        entity.setEmail(googleUser.getEmail());
        entity.setUsername(usernameGenerator.generateFromEmail(googleUser.getEmail()));
        entity.setFirstName(googleUser.getFirstName() != null ? googleUser.getFirstName() : "");
        entity.setLastName(googleUser.getLastName() != null ? googleUser.getLastName() : "");
        entity.setProfileImage(googleUser.getPicture());

        // random unusable password - Google uses authenticate via token, not password
        entity.setPassword(encoder.encode(UUID.randomUUID().toString()));
        entity.setRole(Role.USER);
        return entity;
    }


    @Transactional
    public Operation confirmResetPassword(ResetPasswordRequest resetPasswordRequest) {

        // The token is checked before the user is looked up, so an unknown email, a missing or expired token and a
        // wrong token all get the same answer and the endpoint never tells which emails are registered.
        String resetTokenKey = "reset_token:".concat(resetPasswordRequest.getEmail());
        Object actualResetToken = redisUtil.get(resetTokenKey);

        if (actualResetToken == null || !MessageDigest.isEqual(
                actualResetToken.toString().getBytes(StandardCharsets.UTF_8),
                resetPasswordRequest.getResetToken().getBytes(StandardCharsets.UTF_8))) {
            log.warn("AUTH.RESET.TOKEN.INVALID email={}", EmailMasking.maskEmail(resetPasswordRequest.getEmail()));
            throw new BusinessException(GlobalMessage.INVALID_RESET_TOKEN, HttpStatus.BAD_REQUEST);
        }

        UserEntity user = userRepository.findByEmail(resetPasswordRequest.getEmail())
                .filter(found -> !found.isDeleted())
                .orElse(null);

        // one time use: burn the token before anything else, it never works twice
        redisUtil.delete(resetTokenKey);

        if (user == null) {
            throw new BusinessException(GlobalMessage.INVALID_RESET_TOKEN, HttpStatus.BAD_REQUEST);
        }

        log.info("AUTH.PASSWORD.RESET email={}", EmailMasking.maskEmail(user.getEmail()));
        user.setPassword(encoder.encode(resetPasswordRequest.getNewPassword()));
        userRepository.save(user);

        // whoever knew the old password (or stole a session) must not stay logged in after the reset
        revokeRefreshTokens(user.getId());

        return Operation.UPDATED;
    }

    public PublicUserDTO getUserById(String userId, UUID viewerId) {
        log.debug("USER.GET userId={}", userId);
        try {
            UUID id = UUID.fromString(userId);
            if (userBlockRepository.existsBetween(viewerId, id)) {
                throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            return userRepository.findActiveById(id)
                    .map(this::toPublicUser)
                    .orElseThrow(() ->
                            new BusinessException(
                                    GlobalMessage.NOT_FOUND,
                                    HttpStatus.NOT_FOUND
                            )
                    );
        } catch (IllegalArgumentException illegalArgumentException) { // not a valid UUID Pattern
            log.warn("USER.GET.REJECTED reason=invalid_id userId={}", userId);
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("USER.GET.FAILED userId={}", userId, e);
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaginatedResponse<PublicUserDTO> search(UserSearchRequest request, UUID viewerId, Pageable pageable) {
        log.debug("USER.SEARCH request={}", request);

        // the people the viewer blocked or was blocked by never show up
        List<UUID> hiddenUserIds = userBlockRepository.findRelatedUserIds(viewerId);

        Page<UserEntity> users = userRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.equal(root.get("status"), UserStatus.ACTIVE));
            if (!hiddenUserIds.isEmpty()) {
                predicates.add(cb.not(root.get("id").in(hiddenUserIds)));
            }

            if (request.username() != null && !request.username().isBlank()) {
                // % and _ typed by the user are matched literally, not as wildcards
                predicates.add(cb.like(
                        cb.lower(root.get("username")),
                        LikePatterns.contains(request.username()),
                        LikePatterns.ESCAPE
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        if (users.isEmpty()) {
            throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        List<PublicUserDTO> usersContent = users.getContent().stream()
                .map(this::toPublicUser)
                .toList();

        return new PaginatedResponse<>(
                usersContent,
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages(),
                users.isLast()
        );
    }

    private PublicUserDTO toPublicUser(UserEntity user) {
        return PublicUserDTO.from(user).withProfileImageUrl(profileImageUrlResolver.resolve(user.getProfileImage()));
    }

}
