package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.response.FollowUserResponse;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.service.UserBlockService;
import com.rizkysiregar.narrativ.util.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserBlockController {

    private final UserBlockService userBlockService;

    @io.swagger.v3.oas.annotations.Operation(summary = "Block a user",
            description = "Hides the two users from each other and ends any follow between them. Blocking twice is fine")
    @PostMapping("/{userId}/block")
    @PreAuthorize("hasRole('USER')")
    public Operation block(@PathVariable UUID userId,
                           @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return userBlockService.block(currentUser.getUserId(), userId);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Unblock a user")
    @DeleteMapping("/{userId}/block")
    @PreAuthorize("hasRole('USER')")
    public Operation unblock(@PathVariable UUID userId,
                             @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return userBlockService.unblock(currentUser.getUserId(), userId);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Users the logged-in user blocked")
    @GetMapping("/me/blocks")
    @PreAuthorize("hasRole('USER')")
    public PaginatedResponse<FollowUserResponse> blockedUsers(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return userBlockService.getBlockedUsers(currentUser.getUserId(), page, size);
    }
}
