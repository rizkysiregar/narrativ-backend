package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.util.Operation;

import java.util.UUID;

public interface LikeDislikeService {
    Operation likePost(UUID postId, UUID userId);
    Operation dislikePost(UUID postId, UUID userId);
    PostLikeCountDTO getLikePostCount(UUID postId);
}
