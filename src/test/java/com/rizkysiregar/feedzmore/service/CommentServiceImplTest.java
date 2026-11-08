package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import com.rizkysiregar.feedzmore.model.response.CommentResponse;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.impl.CommentServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostCrudRepository postCrudRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommentServiceImpl commentService;

    private CommentResponse takenDownComment(UUID authorId) {
        return new CommentResponse(UUID.randomUUID(), authorId, "author", null, "rude text",
                ModerationStatus.TAKEN_DOWN, "kata kasar", Instant.now());
    }

    @Test
    void getComment_hidesTheTextOfATakenDownCommentFromOtherUsersButNotFromItsAuthor() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(postCrudRepository.existsInteractableById(postId)).thenReturn(true);
        when(commentRepository.findTopLevelCommentResponses(any(), any(Pageable.class))).thenAnswer(invocation ->
                new PageImpl<>(List.of(takenDownComment(authorId)), PageRequest.of(0, 10), 1));

        PaginatedResponse<CommentResponse> asStranger = commentService.getComment(postId, 0, 10, UUID.randomUUID());
        PaginatedResponse<CommentResponse> asAuthor = commentService.getComment(postId, 0, 10, authorId);

        assertNull(asStranger.getContent().get(0).getCommentContent());
        assertNull(asStranger.getContent().get(0).getTakedownReason());
        assertEquals("rude text", asAuthor.getContent().get(0).getCommentContent());
        assertEquals("kata kasar", asAuthor.getContent().get(0).getTakedownReason());
    }

    @Test
    void getComment_ofATakenDownOrHiddenPostIsNotFound() {
        UUID postId = UUID.randomUUID();
        when(postCrudRepository.existsInteractableById(postId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> commentService.getComment(postId, 0, 10, UUID.randomUUID()));
    }

    @Test
    void getReplies_returnsProjectionsNotEntitiesAndAppliesTheSameRule() {
        UUID parentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(commentRepository.findReplyResponses(parentId)).thenAnswer(invocation -> List.of(takenDownComment(authorId)));

        List<CommentResponse> asStranger = commentService.getReplies(parentId, UUID.randomUUID());

        assertEquals(1, asStranger.size());
        assertNull(asStranger.get(0).getCommentContent());
    }

    @Test
    void addComment_cannotReplyToATakenDownComment() {
        UUID postId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(postCrudRepository.findInteractableById(postId)).thenReturn(Optional.of(PostEntity.builder().id(postId).build()));
        when(commentRepository.findById(parentId)).thenReturn(Optional.of(
                CommentEntity.builder().id(parentId).moderationStatus(ModerationStatus.TAKEN_DOWN).build()));

        assertThrows(EntityNotFoundException.class,
                () -> commentService.addComment(postId, UUID.randomUUID(), "reply", parentId));

        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_onATakenDownPostIsNotFound() {
        UUID postId = UUID.randomUUID();
        when(postCrudRepository.findInteractableById(postId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> commentService.addComment(postId, UUID.randomUUID(), "hi", null));
    }
}
