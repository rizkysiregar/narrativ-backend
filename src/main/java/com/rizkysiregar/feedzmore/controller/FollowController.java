package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.response.FollowStatusResponse;
import com.rizkysiregar.feedzmore.model.response.FollowUserResponse;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.FollowService;
import com.rizkysiregar.feedzmore.util.Operation;
import java.util.UUID;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}")
@PreAuthorize("isAuthenticated()")
public class FollowController {
    private final FollowService followService;

    @io.swagger.v3.oas.annotations.Operation(summary = "Follow an active user")
    @PostMapping("/follow")
    @PreAuthorize("hasRole('USER')")
    public Operation follow(
            @PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal currentUser) {
        return followService.follow(userId, currentUser.getUserId());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Unfollow an active user")
    @DeleteMapping("/follow")
    @PreAuthorize("hasRole('USER')")
    public Operation unfollow(
            @PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal currentUser) {
        return followService.unfollow(userId, currentUser.getUserId());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get an active user's followers")
    @GetMapping("/followers")
    public PaginatedResponse<FollowUserResponse> followers(
            @PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return followService.getFollowers(userId, currentUser.getUserId(), page, size);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get users followed by an active user")
    @GetMapping("/following")
    public PaginatedResponse<FollowUserResponse> following(
            @PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return followService.getFollowing(userId, currentUser.getUserId(), page, size);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get follow status and counts for an active user")
    @GetMapping("/follow-status")
    public FollowStatusResponse followStatus(
            @PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal currentUser) {
        return followService.getFollowStatus(userId, currentUser.getUserId());
    }
}
