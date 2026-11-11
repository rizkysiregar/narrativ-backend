package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.response.CommentResponse;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.request.AddCommentRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.CommentService;
import com.rizkysiregar.feedzmore.util.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @io.swagger.v3.oas.annotations.Operation(summary = "comment on a post")
    @PostMapping("/posts/{postId}/comments")
    @PreAuthorize("hasRole('USER')")
    public Operation addComment(
            @RequestBody AddCommentRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID postId) {
        UUID userId = userPrincipal.getUserId();
        return commentService.addComment(postId, userId, request.getContent(), request.getParentId());
    }


    @io.swagger.v3.oas.annotations.Operation(summary = "Delete a comment or reply; allowed for its author and for the owner of the post")
    @DeleteMapping("/comments/{commentId}")
    @PreAuthorize("hasRole('USER')")
    public Operation deleteComment(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return commentService.deleteComment(commentId, userPrincipal.getUserId());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Getting comments of a post")
    @GetMapping("/posts/{postId}/comments")
    public PaginatedResponse<CommentResponse> getComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return commentService.getComment(postId, page, size, userPrincipal.getUserId());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Getting replies of a top-level comment, paged")
    @GetMapping("/comments/{commentId}/replies")
    public PaginatedResponse<CommentResponse> getRepliesPage(
            @PathVariable UUID commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return commentService.getRepliesPage(commentId, page, size, userPrincipal.getUserId());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get a replay of a comment")
    @GetMapping("/replies/{commentId}")
    List<CommentResponse> getReplies(@PathVariable UUID commentId,
                                     @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return commentService.getReplies(commentId, userPrincipal.getUserId());
    }
}
