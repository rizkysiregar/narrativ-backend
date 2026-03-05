package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.model.request.LikeUnlikePostRequest;
import com.rizkysiregar.feedzmore.service.LikeUnlikeService;
import com.rizkysiregar.feedzmore.util.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/post")
public class LikeController {

    private final LikeUnlikeService likeUnlikeService;

    public LikeController(LikeUnlikeService likeUnlikeService) {
        this.likeUnlikeService = likeUnlikeService;
    }

    @PostMapping("/like")
    public Operation likePost(
            @RequestBody LikeUnlikePostRequest likeUnlikePostRequest,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return likeUnlikeService.likePost(likeUnlikePostRequest, userPrincipal.getUserId());
    }

    @DeleteMapping("/dislike")
    public Operation dislikePost(
            @RequestParam UUID postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return likeUnlikeService.dislikePost(postId, userPrincipal.getUserId());
    }

    @GetMapping("/likes/count")
    public PostLikeCountDTO getLikePostCount(@RequestParam UUID postId) {
        return likeUnlikeService.getLikePostCount(postId);
    }

}
