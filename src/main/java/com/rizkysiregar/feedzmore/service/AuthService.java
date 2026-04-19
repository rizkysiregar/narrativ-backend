package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.dto.GoogleUserInfo;
import com.rizkysiregar.feedzmore.model.request.*;
import com.rizkysiregar.feedzmore.service.auth.GoogleTokenVerifierService;
import com.rizkysiregar.feedzmore.util.RedisUtil;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.dto.UserLoginDTO;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import com.rizkysiregar.feedzmore.util.UsernameGenerator;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    final UserRepository userRepository;
    final AuthenticationManager authManager;
    final JWTService jwtService;
    private final RedisUtil redisUtil;
    private final UserSearchService userSearchService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final UsernameGenerator usernameGenerator;

    public AuthService(UserRepository userRepository,
                       AuthenticationManager authManager,
                       JWTService jwtService,
                       RedisUtil redisUtil,
                       GoogleTokenVerifierService googleTokenVerifierService,
                       UsernameGenerator usernameGenerator,
                       UserSearchService userSearchService
    ) {
        this.userRepository = userRepository;
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.redisUtil = redisUtil;
        this.googleTokenVerifierService = googleTokenVerifierService;
        this.usernameGenerator = usernameGenerator;
        this.userSearchService = userSearchService;
    }

    public Operation register(RegisterUserRequest registerUserRequest) {

        if (userRepository.findByUsername(registerUserRequest.getUsername()).isPresent()) {
            throw new BusinessException(GlobalMessage.USERNAME_ALREADY_TAKEN, HttpStatus.BAD_REQUEST);
        }

        if (userRepository.findByEmail(registerUserRequest.getEmail()).isPresent()) {
            throw new BusinessException(GlobalMessage.EMAIL_ALREADY_TAKEN, HttpStatus.BAD_REQUEST);
        }

        UserEntity entity = new UserEntity();
        entity.setUsername(registerUserRequest.getUsername());
        entity.setPassword(encoder.encode(registerUserRequest.getPassword()));
        entity.setEmail(registerUserRequest.getEmail());
        entity.setFirstName(registerUserRequest.getFirstName());
        entity.setLastName(registerUserRequest.getLastName());
        entity.setPhoneNumber(registerUserRequest.getPhoneNumber());
        entity.setProfileImage(registerUserRequest.getProfileImage());

        userRepository.save(entity);
        userSearchService.indexUser(entity); // dual write into ES
        return Operation.CREATED;
    }

    public UserLoginDTO login(UserLoginRequest userLoginRequest) {
        Authentication authentication =
                authManager.authenticate(new UsernamePasswordAuthenticationToken(userLoginRequest.getUsername(), userLoginRequest.getPassword()));

        if (authentication.isAuthenticated()) {
            Optional<UserEntity> userEntity = userRepository.findByUsername(userLoginRequest.getUsername());

            if (userEntity.isEmpty()) {
                throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }

            String token = jwtService.generateToken(
                    userEntity.get().getUsername(),
                    userEntity.get().getId()
            );

            return UserLoginDTO.builder()
                    .token(token)
                    .username(userEntity.get().getUsername())
                    .userId(userEntity.get().getId().toString())
                    .build();
        }

        throw new BadCredentialsException("Bad Credentials!");
    }

    public UserLoginDTO googleLogin(GoogleLoginRequest request) {
        log.info("GOOGLE.LOGIN");
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

        // Generate JWT - same as regular login
        String token = jwtService.generateToken(user.getUsername(), user.getId());
        return UserLoginDTO.builder()
                .token(token)
                .username(user.getUsername())
                .userId(user.getId().toString())
                .build();
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
        return userRepository.save(entity);
    }


    @Transactional
    public Operation confirmResetPassword(ResetPasswordRequest resetPasswordRequest) {

        UserEntity user = userRepository.findByEmail(resetPasswordRequest.getEmail())
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));

        String resetTokenKey = "reset_token:".concat(resetPasswordRequest.getEmail());
        Object actualResetToken = redisUtil.get(resetTokenKey);

        if (actualResetToken == null) {
            log.info("## resetToken not found on redis");
            throw new BusinessException(GlobalMessage.INVALID_RESET_TOKEN, HttpStatus.BAD_REQUEST);
        }

        if (!actualResetToken.equals(resetPasswordRequest.getResetToken())) {
            log.info("## resetToken not matched!");
            throw new BusinessException(GlobalMessage.INVALID_RESET_TOKEN, HttpStatus.BAD_REQUEST);
        }

        // All good, update password
        log.info("Updating password for user: {}", user.getEmail());
        user.setPassword(encoder.encode(resetPasswordRequest.getNewPassword()));
        userRepository.save(user);

        return Operation.UPDATED;
    }

    public UserDetailDTO getUserById(String userId) {
        log.info("Getting user by userId: {}", userId);
        try {
            return userRepository.findById(UUID.fromString(userId))
                    .map(UserDetailDTO::from)
                    .orElseThrow(() ->
                            new BusinessException(
                                    GlobalMessage.NOT_FOUND,
                                    HttpStatus.NOT_FOUND
                            )
                    );
        } catch (IllegalArgumentException illegalArgumentException) { // not a valid UUID Pattern
            log.error("Illegal argument exception", illegalArgumentException);
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error while getting user by userId: {}", userId, e);
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaginatedResponse<UserDetailDTO> search(UserSearchRequest request, Pageable pageable) {
        log.info("## search users request: {}", request);

        Page<UserEntity> users = userRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.username() != null) {
                predicates.add(cb.like(
                        cb.lower(root.get("username")),
                        "%" + request.username().toLowerCase() + "%"
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        if (users.isEmpty()) {
            throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        List<UserDetailDTO> usersContent = users.getContent().stream()
                .map(UserDetailDTO::from)
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

    // return classic List - untuk ica belajar
    public List<UserDetailDTO> searchList(UserSearchRequest request) {
        log.info("## search users list request: {}", request);

        List<UserEntity> users = userRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.username() != null) {
                predicates.add(cb.like(
                        cb.lower(root.get("username")),
                        "%" + request.username().toLowerCase() + "%"
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        });

        if (users.isEmpty()) {
            throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        return users.stream()
                .map(UserDetailDTO::from)
                .toList();
    }

}
