package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.service.LikeDislikeService;
import com.rizkysiregar.feedzmore.util.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class LikeController {

    private final LikeDislikeService likeDislikeService;

    public LikeController(LikeDislikeService likeDislikeService) {
        this.likeDislikeService = likeDislikeService;
    }

    @PostMapping("/posts/{postId}/like")
    public Operation likePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return likeDislikeService.likePost(postId, userPrincipal.getUserId());
    }

    @DeleteMapping("/posts/{postId}/dislike")
    public Operation dislikePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return likeDislikeService.dislikePost(postId, userPrincipal.getUserId());
    }

    @GetMapping("/posts/{postId}/likes/count")
    public PostLikeCountDTO getLikePostCount(@PathVariable UUID postId) {
        return likeDislikeService.getLikePostCount(postId);
    }

}