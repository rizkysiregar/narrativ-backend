package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.UserLoginDTO;
import com.rizkysiregar.feedzmore.model.dto.UserRegisterDto;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.UserLoginRequest;
import com.rizkysiregar.feedzmore.service.UsersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class UsersController {

    @Autowired
    UsersService usersService;

    @PostMapping("/register")
    public UserRegisterDto register(@RequestBody UserEntity userEntity) {
        log.info("::. Entering /register users");
        return usersService.register(userEntity);
    }

    @PostMapping("/login")
    public UserLoginDTO login(@RequestBody UserLoginRequest userLoginRequest) {
       log.info("::. Entering /login users");
       log.info(":: " + userLoginRequest.toString());
        return usersService.verify(userLoginRequest);
    }

}
