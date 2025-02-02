package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.service.CommentService;
import com.rizkysiregar.feedzmore.util.Operation;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostCrudRepository postCrudRepository;

    @Override
    public Operation addComment(UUID postId, UUID userId, String content, UUID parentId) {
        // add new comment
        PostEntity post = postCrudRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post Not Found"));

        CommentEntity parentComment = null;
        if (parentId != null) {
            parentComment = commentRepository.findById(parentId)
                    .orElseThrow(() -> new EntityNotFoundException("Parent comment not found"));
        }

        CommentEntity comment = CommentEntity.builder()
                .post(post)
                .userId(userId)
                .content(content)
                .parent(parentComment)
                .build();

        commentRepository.save(comment);

        return Operation.CREATED;
    }

    @Override
    public List<CommentEntity> getCommentForPost(UUID postId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtAsc(postId, pageable);
    }

    @Override
    public List<CommentEntity> getReplies(UUID commentId) {
        return commentRepository.findByParentIdOrderByCreatedAtAsc(commentId);
    }

    @Override
    public long getCountByPostId(UUID postId) {
        return commentRepository.contByPostId(postId);
    }


}
