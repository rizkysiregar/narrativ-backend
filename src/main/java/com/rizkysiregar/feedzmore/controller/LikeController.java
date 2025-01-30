package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.model.request.LikeUnlikePostRequest;
import com.rizkysiregar.feedzmore.model.response.LikesDTO;
import com.rizkysiregar.feedzmore.service.LikeUnlikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/post/likes")
public class LikeController {
    @Autowired
    private LikeUnlikeService likeUnlikeService;

    @PostMapping
    public LikesDTO likePost(@RequestBody LikeUnlikePostRequest likeUnlikePostRequest) {
        return likeUnlikeService.likePost(likeUnlikePostRequest);
    }

    @DeleteMapping
    public LikesDTO unlikePost(@RequestBody LikeUnlikePostRequest likeUnlikePostRequest) {
        return likeUnlikeService.unlikePost(likeUnlikePostRequest);
    }

    @GetMapping("/count")
    public PostLikeCountDTO getPostLikeCount(@RequestParam UUID postId) {
        return likeUnlikeService.getPostLikeCount(postId);
    }

}
