package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.model.response.CreatePostResponse;
import com.rizkysiregar.feedzmore.model.response.CreatePostUploadUrlResponse;
import com.rizkysiregar.feedzmore.model.response.PostFeedResponse;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.request.CreatePostUploadUrlRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.util.Operation;

import java.util.UUID;


public interface PostService {
    PaginatedResponse<PostFeedResponse> getExplorePosts(int page, int size, UUID userId, boolean mediaOnly);
    PaginatedResponse<PostFeedResponse> getFollowingPosts(int page, int size, UUID viewerId);
    PaginatedResponse<PostFeedResponse> getAllPostByUserId(int page, int size, String username, UUID viewerId, boolean mediaOnly);
    CreatePostUploadUrlResponse createUploadUrl(UUID userId, CreatePostUploadUrlRequest request);
    CreatePostResponse addNewPost(UUID userId, AddPostRequest addPostRequest);
    Operation deletePost(UUID postId, UUID userId);
}
