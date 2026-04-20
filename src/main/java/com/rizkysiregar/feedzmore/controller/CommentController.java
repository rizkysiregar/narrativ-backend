package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.CommentDTO;
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

    @PostMapping("/posts/{postId}/comments")
    @PreAuthorize("hasRole('USER')")
    public Operation addComment(
            @RequestBody AddCommentRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID postId) {
        UUID userId = userPrincipal.getUserId();
        return commentService.addComment(postId, userId, request.getContent(), request.getParentId());
    }


    @GetMapping("/posts/{postId}/comments")
    public PaginatedResponse<CommentDTO> getComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return commentService.getComment(postId, page, size);
    }

    @GetMapping("/replies/{commentId}")
    List<CommentEntity> getReplies(@PathVariable UUID commentId) {
        return commentService.getReplies(commentId);
    }


}
