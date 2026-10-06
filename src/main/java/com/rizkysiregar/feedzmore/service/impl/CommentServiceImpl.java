package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.mapper.CommentMapper;
import com.rizkysiregar.feedzmore.model.response.CommentResponse;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.CommentService;
import com.rizkysiregar.feedzmore.util.Operation;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
    private final UserRepository userRepository;

    @Override
    public Operation addComment(UUID postId, UUID userId, String content, UUID parentId) {
        // add new comment
        PostEntity post = postCrudRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post Not Found"));

        CommentEntity parentComment = null;
        if (parentId != null) {
            parentComment = commentRepository.findById(parentId)
                    .orElseThrow(() -> new EntityNotFoundException("Parent comment not found"));
        }

        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        CommentEntity comment = CommentEntity.builder()
                .post(post)
                .user(user)
                .content(content)
                .parent(parentComment)
                .build();

        commentRepository.save(comment);

        return Operation.CREATED;
    }

    @Override
    public PaginatedResponse<CommentResponse> getComment(UUID postId, int page, int size) {
        if (!postCrudRepository.existsByIdAndIsDeletedFalse(postId)) {
            throw new EntityNotFoundException("Post Not Found");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentEntity> commentPage = commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtAsc(postId, pageable);

        List<CommentResponse> content = CommentMapper.toDtoList(commentPage.getContent());

        return new PaginatedResponse<>(
                content,
                commentPage.getNumber(),
                commentPage.getSize(),
                commentPage.getTotalElements(),
                commentPage.getTotalPages(),
                commentPage.isLast()
        );
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
