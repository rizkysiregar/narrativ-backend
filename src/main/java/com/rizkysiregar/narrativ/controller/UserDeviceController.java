package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.request.DeactivateDeviceRequest;
import com.rizkysiregar.narrativ.model.request.RegisterDeviceRequest;
import com.rizkysiregar.narrativ.service.UserDeviceService;
import com.rizkysiregar.narrativ.util.Operation;
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

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Register or update users devices"
    )
    @PostMapping()
    public Operation registerDevice(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody @Valid RegisterDeviceRequest request
    ) {
        log.debug("DEVICE.REGISTER username={}", userPrincipal.getUsername());
        userDeviceService.registerOrUpdatedDevice(userPrincipal.getUserId(), request);
        return Operation.CREATED;
    }

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Stop push notifications to this device (logout)",
            description = "Only a token that belongs to the caller is deactivated; an unknown token is ignored"
    )
    @PostMapping("/deactivate")
    public Operation deactivateDevice(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody @Valid DeactivateDeviceRequest request
    ) {
        userDeviceService.deactivateDevice(userPrincipal.getUserId(), request.getPushToken());
        return Operation.DELETED;
    }

}
