package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.model.request.LikeUnlikePostRequest;
import com.rizkysiregar.feedzmore.model.response.LikesDTO;

import java.util.UUID;

public interface LikeUnlikeService {
    LikesDTO likePost(LikeUnlikePostRequest request);
    LikesDTO unlikePost(LikeUnlikePostRequest request);
    PostLikeCountDTO getPostLikeCount(UUID postId);
}
