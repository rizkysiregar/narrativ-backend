package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.util.Operation;

import java.util.List;
import java.util.UUID;

public interface CommentService {
    Operation addComment(UUID postId, UUID userId, String content, UUID parentId);
    List<CommentEntity> getCommentForPost(UUID postId, int page, int size);
    List<CommentEntity> getReplies(UUID commentId);
}
