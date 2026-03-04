package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.CommentDTO;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.request.AddCommentRequest;
import com.rizkysiregar.feedzmore.service.CommentService;
import com.rizkysiregar.feedzmore.util.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @PostMapping("/{postId}")
    public Operation addComment(
            @RequestBody AddCommentRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = userPrincipal.getUserId();
        return commentService.addComment(request.getPostId(), userId, request.getContent(), request.getParentId());
    }


    @GetMapping("/{postId}")
    public List<CommentDTO> getComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return commentService.getCommentForPost(postId, page, size);
    }

    @GetMapping("/replies/{commentId}")
    List<CommentEntity> getReplies(@PathVariable UUID commentId) {
        return commentService.getReplies(commentId);
    }


}
