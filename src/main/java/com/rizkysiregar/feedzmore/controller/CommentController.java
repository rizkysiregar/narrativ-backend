package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.service.CommentService;
import com.rizkysiregar.feedzmore.util.Operation;
import lombok.RequiredArgsConstructor;
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
            @PathVariable UUID postId,
            @RequestParam UUID userId,
            @RequestParam String content,
            @RequestParam(required = false) UUID parentId
    ) {
        return commentService.addComment(postId, userId, content, parentId);
    }


    @GetMapping("/{postId}")
    public List<CommentEntity> getComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size
    ) {
        return commentService.getCommentForPost(postId, page, size);
    }

    @GetMapping("/replies/{commentId}")
    List<CommentEntity> getReplies(@PathVariable UUID commentId) {
        return commentService.getReplies(commentId);
    }


}
