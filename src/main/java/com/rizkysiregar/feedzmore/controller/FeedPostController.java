package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.AddPostDTO;
import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.cfg.defs.UUIDDef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/posts")
public class FeedPostController {

    private final PostService postService;

    public FeedPostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public PaginatedResponse<PostDTO> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = userPrincipal.getUserId();
        return postService.getAllPost(page, size, userId);
    }

    @GetMapping("/user")
    public PaginatedResponse<PostDTO> getAllPostByUserId(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam String username
    ) {
        return postService.getAllPostByUserId(page, size, username);
    }

    @Operation(summary = "Upload new post feed")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AddPostDTO upload(
            @Parameter(
                    description = "Other payloads that related to the post",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AddPostRequest.class)
                    )
            )
            @RequestPart(value = "addPostRequest") AddPostRequest addPostRequest,

            @Parameter(description = "A image that want to post", required = true)
            @RequestPart(value = "image") MultipartFile image
    ) throws Exception {
        return postService.addNewPost(addPostRequest, image);
    }


}