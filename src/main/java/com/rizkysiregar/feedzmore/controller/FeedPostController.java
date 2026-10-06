package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.response.CreatePostResponse;
import com.rizkysiregar.feedzmore.model.response.PostFeedResponse;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Operation(summary = "Get all feed post for Home feed page")
    @GetMapping
    public PaginatedResponse<PostFeedResponse> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = userPrincipal.getUserId();
        return postService.getAllPost(page, size, userId);
    }

    @Operation(summary = "Getting feed user specific feed post by username")
    @GetMapping("/users/{username}/posts")
    public PaginatedResponse<PostFeedResponse> getAllPostByUserId(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @PathVariable String username) {
        return postService.getAllPostByUserId(page, size, username);
    }

    @Operation(summary = "Upload new post feed")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public CreatePostResponse upload(
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

    @Operation(summary = "Soft delete own feed post")
    @DeleteMapping("/{postId}")
    @PreAuthorize("hasRole('USER')")
    public com.rizkysiregar.feedzmore.util.Operation deletePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.deletePost(postId, userPrincipal.getUserId());
    }


}
