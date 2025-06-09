package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.config.RedisUtil;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.dto.UserLoginDTO;
import com.rizkysiregar.feedzmore.model.dto.UserRegisterDto;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.ResetPasswordRequest;
import com.rizkysiregar.feedzmore.model.request.UserLoginRequest;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
public class UsersService {

    @Autowired
    UserRepository userRepository;
    @Autowired
    AuthenticationManager authManager;
    @Autowired
    JWTService jwtService;
    @Autowired
    private RedisUtil redisUtil;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public UserRegisterDto register(UserEntity userEntity) {
        if (userEntity.getUsername() == null || userEntity.getPassword() == null) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        userEntity.setPassword(encoder.encode(userEntity.getPassword()));
        userRepository.save(userEntity);
        return UserRegisterDto.builder()
                .username(userEntity.getUsername())
                .build();
    }

    public UserLoginDTO verify(UserLoginRequest userLoginRequest) {
        Authentication authentication =
                authManager.authenticate(new UsernamePasswordAuthenticationToken(userLoginRequest.getUsername(), userLoginRequest.getPassword()));

        if (authentication.isAuthenticated()) {
            Optional<UserEntity> userEntity = userRepository.findByUsername(userLoginRequest.getUsername());

            if (userEntity.isEmpty()) {
                throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }

            return UserLoginDTO.builder()
                    .token(jwtService.generateToken(userLoginRequest.getUsername()))
                    .username(userLoginRequest.getUsername())
                    .userId(userEntity.get().getId().toString())
                    .build();
        }

        throw new BadCredentialsException("Bad Credentials!");
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

}
