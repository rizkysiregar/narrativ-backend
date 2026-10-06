package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.response.CommentResponse;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.util.Operation;

import java.util.List;
import java.util.UUID;

public interface CommentService {
    Operation addComment(UUID postId, UUID userId, String content, UUID parentId);
    PaginatedResponse<CommentResponse> getComment(UUID postId, int page, int size);
    List<CommentEntity> getReplies(UUID commentId);
    long getCountByPostId(UUID postId);
}
