package com.rizkysiregar.feedzmore.model.request;

public record AdminCommentSearchRequest(
        String postId,
        String authorId,
        String moderationStatus,
        String keyword
) {}
