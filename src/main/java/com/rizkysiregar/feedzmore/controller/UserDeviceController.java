package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.request.RegisterDeviceRequest;
import com.rizkysiregar.feedzmore.service.UserDeviceService;
import com.rizkysiregar.feedzmore.util.Operation;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Slf4j
public class UserDeviceController {

    private final UserDeviceService userDeviceService;

    @ApiOperation(
            value = "Register or update users devices"
    )
    @PostMapping()
    public Operation registerDevice(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody @Valid RegisterDeviceRequest request
    ) {
        log.info("Registering device for user {}", userPrincipal.getUsername());
        userDeviceService.registerOrUpdatedDevice(userPrincipal.getUserId(), request);
        return Operation.CREATED;
    }

}
