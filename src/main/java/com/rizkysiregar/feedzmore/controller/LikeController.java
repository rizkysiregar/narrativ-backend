package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.service.LikeDislikeService;
import com.rizkysiregar.feedzmore.util.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class LikeController {

    private final LikeDislikeService likeDislikeService;

    public LikeController(LikeDislikeService likeDislikeService) {
        this.likeDislikeService = likeDislikeService;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Like a post")
    @PostMapping("/posts/{postId}/like")
    @PreAuthorize("hasRole('USER')")
    public Operation likePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return likeDislikeService.likePost(postId, userPrincipal.getUserId());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "If you already like a post and want to undo or dislike, hit this api")
    @DeleteMapping("/posts/{postId}/dislike")
    @PreAuthorize("hasRole('USER')")
    public Operation dislikePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return likeDislikeService.dislikePost(postId, userPrincipal.getUserId());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get likes count of a post")
    @GetMapping("/posts/{postId}/likes/count")
    public PostLikeCountDTO getLikePostCount(@PathVariable UUID postId) {
        return likeDislikeService.getLikePostCount(postId);
    }

}