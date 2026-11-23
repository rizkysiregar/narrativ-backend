package com.rizkysiregar.narrativ.service;


import com.rizkysiregar.narrativ.model.response.CreatePostResponse;
import com.rizkysiregar.narrativ.model.response.CreatePostUploadUrlResponse;
import com.rizkysiregar.narrativ.model.response.PostFeedResponse;
import com.rizkysiregar.narrativ.model.request.AddPostRequest;
import com.rizkysiregar.narrativ.model.request.CreatePostUploadUrlRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.util.Operation;

import java.util.UUID;


public interface PostService {
    /**
     * The feeds page either by number ({@code page}) or, when {@code cursor} is given, after the post it points at
     * ({@link com.rizkysiregar.narrativ.util.FeedCursor}). A cursor page has no totals (they are -1) and ignores page.
     */
    PaginatedResponse<PostFeedResponse> getExplorePosts(int page, int size, String cursor, UUID userId, boolean mediaOnly);
    PaginatedResponse<PostFeedResponse> getFollowingPosts(int page, int size, String cursor, UUID viewerId);
    PostFeedResponse getPostById(UUID postId, UUID viewerId);
    PaginatedResponse<PostFeedResponse> getAllPostByUserId(int page, int size, String cursor, String username, UUID viewerId, boolean mediaOnly);

    /**
     * The posts whose caption holds every word of {@code query} (each also as the start of a longer word), newest
     * first, with the visibility of explore. Pages by cursor only; the totals are -1.
     */
    PaginatedResponse<PostFeedResponse> searchPosts(String query, int size, String cursor, UUID viewerId);

    default PaginatedResponse<PostFeedResponse> getExplorePosts(int page, int size, UUID userId, boolean mediaOnly) {
        return getExplorePosts(page, size, null, userId, mediaOnly);
    }

    default PaginatedResponse<PostFeedResponse> getFollowingPosts(int page, int size, UUID viewerId) {
        return getFollowingPosts(page, size, null, viewerId);
    }

    default PaginatedResponse<PostFeedResponse> getAllPostByUserId(int page, int size, String username, UUID viewerId, boolean mediaOnly) {
        return getAllPostByUserId(page, size, null, username, viewerId, mediaOnly);
    }
    CreatePostUploadUrlResponse createUploadUrl(UUID userId, CreatePostUploadUrlRequest request);
    CreatePostResponse addNewPost(UUID userId, AddPostRequest addPostRequest);
    Operation deletePost(UUID postId, UUID userId);
}
