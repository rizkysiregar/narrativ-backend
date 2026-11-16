package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.dto.PostLikeCountDTO;
import com.rizkysiregar.narrativ.util.Operation;

import java.util.UUID;

public interface LikeDislikeService {
    Operation likePost(UUID postId, UUID userId);
    Operation dislikePost(UUID postId, UUID userId);
    PostLikeCountDTO getLikePostCount(UUID postId);
}
