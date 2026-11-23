package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.response.CreatePostResponse;
import com.rizkysiregar.narrativ.model.response.CreatePostUploadUrlResponse;
import com.rizkysiregar.narrativ.model.response.PostFeedResponse;
import com.rizkysiregar.narrativ.model.request.AddPostRequest;
import com.rizkysiregar.narrativ.model.request.CreatePostUploadUrlRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.service.PostService;
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

    @Operation(summary = "Home feed: your own posts and those of the people you follow, newest first, with 3 suggestions (ranked posts of people you do not follow, suggested=true) after every 10 of them; when they run out, suggestions fill the pages. Page 0 and every cursor page are built this way and have no totals (-1): pass the nextCursor of a page as cursor to get the next one (page is then ignored). A numbered page after the first holds the followed posts only.")
    @GetMapping
    public PaginatedResponse<PostFeedResponse> getHomePosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.getFollowingPosts(page, size, cursor, userPrincipal.getUserId());
    }

    @Operation(summary = "Explore feed: every post you may see except taken down ones, the recent ones ranked by likes, comments and age (people you follow and your own posts lower, at most 2 of one author in every 10), then older ones by date. Totals are -1; page through with nextCursor. mediaOnly=true leaves out caption only posts (grid view).")
    @GetMapping("/explore")
    public PaginatedResponse<PostFeedResponse> getExplorePosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean mediaOnly,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.getExplorePosts(page, size, cursor, userPrincipal.getUserId(), mediaOnly);
    }

    @Operation(summary = "Search posts by the words of their caption",
            description = "q is 2 to 50 characters (400 otherwise). Every word of q must be in the caption, each also as the start of a longer word (\"kopi sus\" finds \"kopi susu\"); a hashtag counts as a word (#coffee is found by coffee). Newest first, same visibility as explore (no deleted or taken down posts, no blocked people). Page through with nextCursor; totals are -1.")
    @GetMapping("/search")
    public PaginatedResponse<PostFeedResponse> searchPosts(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.searchPosts(q, size, cursor, userPrincipal.getUserId());
    }

    @Operation(summary = "Get one post by id, e.g. to open it from a push notification. 404 when it is deleted, its author is gone or banned, or either side blocked the other. A taken down post is returned without caption and media, except to its author.")
    @GetMapping("/{postId}")
    public PostFeedResponse getPost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.getPostById(postId, userPrincipal.getUserId());
    }

    @Operation(summary = "Same as GET /posts")
    @GetMapping("/following")
    public PaginatedResponse<PostFeedResponse> getFollowingPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.getFollowingPosts(page, size, cursor, userPrincipal.getUserId());
    }

    @Operation(summary = "Getting feed user specific feed post by username. mediaOnly=true leaves out caption only posts (grid view).")
    @GetMapping("/users/{username}/posts")
    public PaginatedResponse<PostFeedResponse> getAllPostByUserId(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean mediaOnly,
            @RequestParam(required = false) String cursor,
            @PathVariable String username,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return postService.getAllPostByUserId(page, size, cursor, username, userPrincipal.getUserId(), mediaOnly);
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
    public com.rizkysiregar.narrativ.util.Operation deletePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return postService.deletePost(postId, userPrincipal.getUserId());
    }


}
