package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.response.CreatePostResponse;
import com.rizkysiregar.feedzmore.model.response.CreatePostUploadUrlResponse;
import com.rizkysiregar.feedzmore.model.response.PostFeedResponse;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.request.CreatePostUploadUrlRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/posts")
public class FeedPostController {

    private final PostService postService;

    public FeedPostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "Get posts from followed users for Home feed")
    @GetMapping
    public PaginatedResponse<PostFeedResponse> getHomePosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.getFollowingPosts(page, size, userPrincipal.getUserId());
    }

    @Operation(summary = "Get all public posts for Explore feed. mediaOnly=true leaves out caption only posts (grid view).")
    @GetMapping("/explore")
    public PaginatedResponse<PostFeedResponse> getExplorePosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean mediaOnly,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.getExplorePosts(page, size, userPrincipal.getUserId(), mediaOnly);
    }

    @Operation(summary = "Get posts from followed users")
    @GetMapping("/following")
    public PaginatedResponse<PostFeedResponse> getFollowingPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.getFollowingPosts(page, size, userPrincipal.getUserId());
    }

    @Operation(summary = "Getting feed user specific feed post by username. mediaOnly=true leaves out caption only posts (grid view).")
    @GetMapping("/users/{username}/posts")
    public PaginatedResponse<PostFeedResponse> getAllPostByUserId(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean mediaOnly,
            @PathVariable String username,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return postService.getAllPostByUserId(page, size, username, userPrincipal.getUserId(), mediaOnly);
    }

    @Operation(summary = "Get presigned upload URL for post media. Client uploads the file directly to object storage using this URL.")
    @PostMapping(value = "/upload-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('USER')")
    public CreatePostUploadUrlResponse createUploadUrl(
            @RequestBody CreatePostUploadUrlRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.createUploadUrl(userPrincipal.getUserId(), request);
    }

    @Operation(summary = "Create a new post: a caption (max 500 chars), up to 5 media uploaded via presigned URL (images and videos can be mixed), or both. A post needs a caption or at least one media.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('USER')")
    public CreatePostResponse upload(
            @RequestBody AddPostRequest addPostRequest,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.addNewPost(userPrincipal.getUserId(), addPostRequest);
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
