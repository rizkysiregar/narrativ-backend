package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.dto.UserLoginDto;
import com.rizkysiregar.feedzmore.model.dto.UserRegisterDto;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.entity.UsersEntity;
import com.rizkysiregar.feedzmore.model.request.UserLoginRequest;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsersService {

    @Autowired
    UserRepository userRepository;
    @Autowired
    AuthenticationManager authManager;
    @Autowired
    JWTService jwtService;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public UserRegisterDto register(UsersEntity usersEntity) {
        if (usersEntity.getUsername() == null || usersEntity.getPassword() == null) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        usersEntity.setPassword(encoder.encode(usersEntity.getPassword()));
        userRepository.save(usersEntity);
        return UserRegisterDto.builder()
                .username(usersEntity.getUsername())
                .build();
    }

    public UserLoginDto verify(UserLoginRequest userLoginRequest) {
        Authentication authentication =
                authManager.authenticate(new UsernamePasswordAuthenticationToken(userLoginRequest.getUsername(), userLoginRequest.getPassword()));

        if (authentication.isAuthenticated()) {
            return UserLoginDto.builder()
                    .token(jwtService.generateToken(userLoginRequest.getUsername()))
                    .build();
        }

        throw new BadCredentialsException("Bad Credentials!");
    }
}
