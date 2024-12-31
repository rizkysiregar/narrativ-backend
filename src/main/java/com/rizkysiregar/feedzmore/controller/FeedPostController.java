package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.AddPostDTO;
import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/posts")
public class FeedPostController {
    @Autowired
    private PostService postService;

    @GetMapping
    public PaginatedResponse<PostDTO> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return postService.getAllPost(page, size);
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public AddPostDTO upload(@RequestPart("post") AddPostRequest addPostRequest,
                             @RequestParam("image") MultipartFile image) {
        return postService.addNewPost(addPostRequest, image);
    }

}
