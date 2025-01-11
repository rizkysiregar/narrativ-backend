package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.UserLoginDTO;
import com.rizkysiregar.feedzmore.model.dto.UserRegisterDto;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.UserLoginRequest;
import com.rizkysiregar.feedzmore.service.UsersService;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@Tag(name = "Users API")
public class UsersController {

    @Autowired
    UsersService usersService;

    @ApiOperation(value = "Register a new user", notes = "use this to register a new user")
    @PostMapping("/register")
    public UserRegisterDto register(@RequestBody UserEntity userEntity) {
        log.info("::. Entering /register users");
        return usersService.register(userEntity);
    }

    @ApiOperation(value = "User login", notes = "use this to gain jwt credentials for user validity")
    @PostMapping("/login")
    public UserLoginDTO login(@RequestBody UserLoginRequest userLoginRequest) {
       log.info("::. Entering /login users");
       log.info(":: " + userLoginRequest.toString());
        return usersService.verify(userLoginRequest);
    }

}
