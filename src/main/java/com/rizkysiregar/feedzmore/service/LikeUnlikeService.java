package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.model.request.LikeUnlikePostRequest;
import com.rizkysiregar.feedzmore.util.Operation;

import java.util.UUID;

public interface LikeUnlikeService {
    Operation likePost(LikeUnlikePostRequest request, UUID userId);
    Operation dislikePost(UUID postId, UUID userId);
    PostLikeCountDTO getLikePostCount(UUID postId);
}
