package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.model.request.LikeUnlikePostRequest;
import com.rizkysiregar.feedzmore.service.LikeUnlikeService;
import com.rizkysiregar.feedzmore.util.Operation;
import org.springframework.beans.factory.annotation.Autowired;
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
    public Operation likePost(@RequestBody LikeUnlikePostRequest likeUnlikePostRequest) {
        return likeUnlikeService.likePost(likeUnlikePostRequest);
    }

    @DeleteMapping("/unlike")
    public Operation unlikePost(@RequestParam UUID postId, @RequestParam UUID userId) {
        return likeUnlikeService.unlikePost(postId, userId);
    }

    @GetMapping("/likes/count")
    public PostLikeCountDTO getPostLikeCount(@RequestParam UUID postId) {
        return likeUnlikeService.getPostLikeCount(postId);
    }

}
