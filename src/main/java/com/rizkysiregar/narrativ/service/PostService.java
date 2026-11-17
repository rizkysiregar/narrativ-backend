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
    PaginatedResponse<PostFeedResponse> getExplorePosts(int page, int size, UUID userId, boolean mediaOnly);
    PaginatedResponse<PostFeedResponse> getFollowingPosts(int page, int size, UUID viewerId);
    PaginatedResponse<PostFeedResponse> getAllPostByUserId(int page, int size, String username, UUID viewerId, boolean mediaOnly);
    CreatePostUploadUrlResponse createUploadUrl(UUID userId, CreatePostUploadUrlRequest request);
    CreatePostResponse addNewPost(UUID userId, AddPostRequest addPostRequest);
    Operation deletePost(UUID postId, UUID userId);
}
