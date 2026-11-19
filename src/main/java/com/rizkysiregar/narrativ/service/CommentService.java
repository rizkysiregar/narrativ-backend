package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.response.CommentResponse;
import com.rizkysiregar.narrativ.model.entity.CommentEntity;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.util.Operation;

import java.util.List;
import java.util.UUID;

public interface CommentService {
    default Operation addComment(UUID postId, UUID userId, String content, UUID parentId) {
        return addComment(postId, userId, content, parentId, null);
    }

    /** [quoteText] is words of the post's caption a top-level comment answers, null for a plain comment. */
    Operation addComment(UUID postId, UUID userId, String content, UUID parentId, String quoteText);

    /** Pins a top-level comment of the post on top of its comments; only the post's author may. */
    Operation pinComment(UUID postId, UUID commentId, UUID requesterId);

    Operation unpinComment(UUID postId, UUID requesterId);
    PaginatedResponse<CommentResponse> getComment(UUID postId, int page, int size, UUID viewerId);
    List<CommentResponse> getReplies(UUID commentId, UUID viewerId);
    PaginatedResponse<CommentResponse> getRepliesPage(UUID commentId, int page, int size, UUID viewerId);
    Operation deleteComment(UUID commentId, UUID requesterId);
    long getCountByPostId(UUID postId, UUID viewerId);
}
