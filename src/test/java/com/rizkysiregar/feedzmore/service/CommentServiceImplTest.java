package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.messaging.event.CommentRepliedEvent;
import com.rizkysiregar.feedzmore.messaging.publisher.CommentEventPublisher;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import com.rizkysiregar.feedzmore.model.response.CommentResponse;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.impl.CommentServiceImpl;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.util.Operation;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private CommentEventPublisher commentEventPublisher;
    @Mock
    private ProfileImageUrlResolver profileImageUrlResolver;

    @InjectMocks
    private CommentServiceImpl commentService;

    private CommentResponse takenDownComment(UUID authorId) {
        return new CommentResponse(UUID.randomUUID(), authorId, "author", null, "rude text",
                ModerationStatus.TAKEN_DOWN, "kata kasar", Instant.now(), null, null, null, null, 0L);
    }

    @Test
    void getComment_hidesTheTextOfATakenDownCommentFromOtherUsersButNotFromItsAuthor() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(postCrudRepository.findInteractableById(postId)).thenReturn(Optional.of(PostEntity.builder()
                .id(postId).user(user("owner")).build()));
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
        when(postCrudRepository.findInteractableById(postId)).thenReturn(Optional.empty());

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

    private UserEntity user(String username) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        return user;
    }

    private CommentEntity comment(PostEntity post, UserEntity author, CommentEntity parent) {
        return CommentEntity.builder().id(UUID.randomUUID()).post(post).user(author).parent(parent).build();
    }

    private CommentEntity savedComment() {
        ArgumentCaptor<CommentEntity> saved = ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void addComment_replyToATopLevelCommentPointsParentAndReplyToAtIt() {
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).build();
        UserEntity author = user("ani");
        UserEntity replier = user("budi");
        CommentEntity root = comment(post, author, null);
        when(postCrudRepository.findInteractableById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(root.getId())).thenReturn(Optional.of(root));
        when(userRepository.findById(replier.getId())).thenReturn(Optional.of(replier));

        commentService.addComment(post.getId(), replier.getId(), "setuju", root.getId());

        CommentEntity saved = savedComment();
        assertSame(root, saved.getParent());
        assertSame(root, saved.getReplyTo());
    }

    @Test
    void addComment_replyToAReplyStaysOneLevelDeepAndTagsTheAnsweredComment() {
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).build();
        UserEntity ani = user("ani");
        UserEntity budi = user("budi");
        UserEntity citra = user("citra");
        CommentEntity root = comment(post, ani, null);
        CommentEntity replyOfBudi = comment(post, budi, root);
        when(postCrudRepository.findInteractableById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(replyOfBudi.getId())).thenReturn(Optional.of(replyOfBudi));
        when(userRepository.findById(citra.getId())).thenReturn(Optional.of(citra));

        commentService.addComment(post.getId(), citra.getId(), "kok bisa?", replyOfBudi.getId());

        CommentEntity saved = savedComment();
        assertSame(root, saved.getParent());
        assertSame(replyOfBudi, saved.getReplyTo());

        ArgumentCaptor<CommentRepliedEvent> event = ArgumentCaptor.forClass(CommentRepliedEvent.class);
        verify(commentEventPublisher).publishCommentReplied(event.capture());
        assertEquals(budi.getId(), event.getValue().getRecipientId());
        assertEquals("citra", event.getValue().getReplierUsername());
    }

    @Test
    void addComment_replyingToYourOwnCommentDoesNotNotifyAnyone() {
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).build();
        UserEntity ani = user("ani");
        CommentEntity root = comment(post, ani, null);
        when(postCrudRepository.findInteractableById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(root.getId())).thenReturn(Optional.of(root));
        when(userRepository.findById(ani.getId())).thenReturn(Optional.of(ani));

        commentService.addComment(post.getId(), ani.getId(), "tambahan", root.getId());

        verifyNoInteractions(commentEventPublisher);
    }

    @Test
    void addComment_topLevelCommentDoesNotNotifyAnyone() {
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).build();
        UserEntity ani = user("ani");
        when(postCrudRepository.findInteractableById(post.getId())).thenReturn(Optional.of(post));
        when(userRepository.findById(ani.getId())).thenReturn(Optional.of(ani));

        commentService.addComment(post.getId(), ani.getId(), "halo", null);

        verifyNoInteractions(commentEventPublisher);
    }

    @Test
    void addComment_cannotReplyToACommentOfAnotherPost() {
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).build();
        PostEntity otherPost = PostEntity.builder().id(UUID.randomUUID()).build();
        CommentEntity foreign = comment(otherPost, user("ani"), null);
        when(postCrudRepository.findInteractableById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(EntityNotFoundException.class,
                () -> commentService.addComment(post.getId(), UUID.randomUUID(), "reply", foreign.getId()));

        verify(commentRepository, never()).save(any());
    }

    @Test
    void getComment_flagsWhoMayDeleteEachComment() {
        UUID postId = UUID.randomUUID();
        UUID postOwnerId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UserEntity postOwner = user("owner");
        postOwner.setId(postOwnerId);
        when(postCrudRepository.findInteractableById(postId)).thenReturn(Optional.of(PostEntity.builder()
                .id(postId).user(postOwner).build()));
        when(commentRepository.findTopLevelCommentResponses(any(), any(Pageable.class))).thenAnswer(invocation ->
                new PageImpl<>(List.of(takenDownComment(authorId)), PageRequest.of(0, 10), 1));

        assertEquals(true, commentService.getComment(postId, 0, 10, authorId).getContent().get(0).isCanDelete());
        assertEquals(true, commentService.getComment(postId, 0, 10, postOwnerId).getContent().get(0).isCanDelete());
        assertEquals(false, commentService.getComment(postId, 0, 10, UUID.randomUUID()).getContent().get(0).isCanDelete());
    }

    @Test
    void deleteComment_authorCanDeleteTheirReplyAndTagsOfAnswersToItAreCleared() {
        UserEntity postOwner = user("owner");
        UserEntity author = user("ani");
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(postOwner).build();
        CommentEntity root = comment(post, postOwner, null);
        CommentEntity reply = comment(post, author, root);
        when(commentRepository.findById(reply.getId())).thenReturn(Optional.of(reply));

        assertEquals(Operation.DELETED, commentService.deleteComment(reply.getId(), author.getId()));

        verify(commentRepository).clearReplyTo(reply.getId());
        verify(commentRepository, never()).deleteAllByParentId(any());
        verify(commentRepository).delete(reply);
    }

    @Test
    void deleteComment_postOwnerCanDeleteAnyCommentAndItsRepliesGoWithIt() {
        UserEntity postOwner = user("owner");
        UserEntity author = user("ani");
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(postOwner).build();
        CommentEntity root = comment(post, author, null);
        when(commentRepository.findById(root.getId())).thenReturn(Optional.of(root));

        assertEquals(Operation.DELETED, commentService.deleteComment(root.getId(), postOwner.getId()));

        verify(commentRepository).deleteAllByParentId(root.getId());
        verify(commentRepository).delete(root);
    }

    @Test
    void deleteComment_anotherUserIsForbidden() {
        UserEntity postOwner = user("owner");
        UserEntity author = user("ani");
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).user(postOwner).build();
        CommentEntity root = comment(post, author, null);
        when(commentRepository.findById(root.getId())).thenReturn(Optional.of(root));

        BusinessException e = assertThrows(BusinessException.class,
                () -> commentService.deleteComment(root.getId(), UUID.randomUUID()));

        assertEquals(HttpStatus.FORBIDDEN, e.getHttpStatus());
        verify(commentRepository, never()).delete(any(CommentEntity.class));
    }

    @Test
    void deleteComment_unknownCommentIsNotFound() {
        UUID id = UUID.randomUUID();
        when(commentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> commentService.deleteComment(id, UUID.randomUUID()));
    }

    @Test
    void getComment_rejectsAPageOrSizeOutsideTheLimitsWithoutTouchingTheDatabase() {
        UUID postId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        assertBadRequest(() -> commentService.getComment(postId, -1, 10, viewerId));
        assertBadRequest(() -> commentService.getComment(postId, 0, 0, viewerId));
        assertBadRequest(() -> commentService.getComment(postId, 0, 51, viewerId));
        verifyNoInteractions(postCrudRepository, commentRepository);
    }

    @Test
    void getRepliesPage_rejectsAPageOrSizeOutsideTheLimitsWithoutTouchingTheDatabase() {
        UUID commentId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        assertBadRequest(() -> commentService.getRepliesPage(commentId, -1, 10, viewerId));
        assertBadRequest(() -> commentService.getRepliesPage(commentId, 0, 51, viewerId));
        verifyNoInteractions(commentRepository);
    }

    @Test
    void addComment_rejectsABlankOrMissingText() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertBadRequest(() -> commentService.addComment(postId, userId, null, null));
        assertBadRequest(() -> commentService.addComment(postId, userId, "   ", null));
        verifyNoInteractions(commentRepository);
    }

    @Test
    void addComment_savesTheTextWithoutSurroundingSpaces() {
        PostEntity post = PostEntity.builder().id(UUID.randomUUID()).build();
        UserEntity ani = user("ani");
        when(postCrudRepository.findInteractableById(post.getId())).thenReturn(Optional.of(post));
        when(userRepository.findById(ani.getId())).thenReturn(Optional.of(ani));

        commentService.addComment(post.getId(), ani.getId(), "  halo  ", null);

        assertEquals("halo", savedComment().getContent());
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable call) {
        BusinessException error = assertThrows(BusinessException.class, call);
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    }
}
