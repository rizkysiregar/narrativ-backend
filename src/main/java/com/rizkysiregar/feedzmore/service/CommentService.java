package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.response.CommentResponse;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.util.Operation;

import java.util.List;
import java.util.UUID;

public interface CommentService {
    Operation addComment(UUID postId, UUID userId, String content, UUID parentId);
    PaginatedResponse<CommentResponse> getComment(UUID postId, int page, int size, UUID viewerId);
    List<CommentResponse> getReplies(UUID commentId, UUID viewerId);
    PaginatedResponse<CommentResponse> getRepliesPage(UUID commentId, int page, int size, UUID viewerId);
    Operation deleteComment(UUID commentId, UUID requesterId);
    long getCountByPostId(UUID postId);
}
