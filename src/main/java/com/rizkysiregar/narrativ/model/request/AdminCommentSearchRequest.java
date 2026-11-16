package com.rizkysiregar.narrativ.model.request;

public record AdminCommentSearchRequest(
        String postId,
        String authorId,
        String moderationStatus,
        String keyword
) {}
