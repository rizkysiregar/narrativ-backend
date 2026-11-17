package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.response.CommentResponse;
import com.rizkysiregar.narrativ.model.entity.CommentEntity;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.util.Operation;

import java.util.List;
import java.util.UUID;

public interface CommentService {
    Operation addComment(UUID postId, UUID userId, String content, UUID parentId);
    PaginatedResponse<CommentResponse> getComment(UUID postId, int page, int size, UUID viewerId);
    List<CommentResponse> getReplies(UUID commentId, UUID viewerId);
    PaginatedResponse<CommentResponse> getRepliesPage(UUID commentId, int page, int size, UUID viewerId);
    Operation deleteComment(UUID commentId, UUID requesterId);
    long getCountByPostId(UUID postId, UUID viewerId);
}
