package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.model.response.CreatePostResponse;
import com.rizkysiregar.feedzmore.model.response.PostFeedResponse;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.util.Operation;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;


public interface PostService {
    PaginatedResponse<PostFeedResponse> getExplorePosts(int page, int size, UUID userId);
    PaginatedResponse<PostFeedResponse> getFollowingPosts(int page, int size, UUID viewerId);
    PaginatedResponse<PostFeedResponse> getAllPostByUserId(int page, int size, String username, UUID viewerId);
    CreatePostResponse addNewPost(UUID userId, AddPostRequest addPostRequest, MultipartFile media) throws Exception;
    Operation deletePost(UUID postId, UUID userId);
}
