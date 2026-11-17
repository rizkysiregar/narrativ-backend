package com.rizkysiregar.narrativ.repository;

import com.rizkysiregar.narrativ.model.dto.PostCountDTO;
import com.rizkysiregar.narrativ.model.entity.CommentEntity;
import com.rizkysiregar.narrativ.model.response.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, UUID>, JpaSpecificationExecutor<CommentEntity> {
    @Query(value = """
            SELECT new com.rizkysiregar.narrativ.model.response.CommentResponse(
                c.id, c.user.id, c.user.username, c.user.profileImage, c.content,
                c.moderationStatus, c.moderationReason, c.moderatedAt,
                c.parent.id, rt.id, ru.username, c.createdAt,
                (SELECT COUNT(r) FROM CommentEntity r WHERE r.parent.id = c.id AND r.user.isDeleted = false AND r.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = r.user.id) OR (b.blockerId = r.user.id AND b.blockedId = :viewerId))))
            FROM CommentEntity c LEFT JOIN c.replyTo rt LEFT JOIN rt.user ru
            WHERE c.post.id = :postId AND c.parent IS NULL AND c.user.isDeleted = false AND c.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = c.user.id) OR (b.blockerId = c.user.id AND b.blockedId = :viewerId))
            ORDER BY c.createdAt ASC, c.id ASC
            """, countQuery = """
            SELECT COUNT(c) FROM CommentEntity c
            WHERE c.post.id = :postId AND c.parent IS NULL AND c.user.isDeleted = false AND c.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = c.user.id) OR (b.blockerId = c.user.id AND b.blockedId = :viewerId))
            """)
    Page<CommentResponse> findTopLevelCommentResponses(@Param("postId") UUID postId, @Param("viewerId") UUID viewerId, Pageable pageable);

    @Query("""
            SELECT new com.rizkysiregar.narrativ.model.dto.PostCountDTO(c.post.id, COUNT(c))
            FROM CommentEntity c WHERE c.post.id IN :postIds AND c.user.isDeleted = false AND c.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = c.user.id) OR (b.blockerId = c.user.id AND b.blockedId = :viewerId)) GROUP BY c.post.id
            """)
    List<PostCountDTO> countByPostIds(@Param("postIds") List<UUID> postIds, @Param("viewerId") UUID viewerId);

    // replies of a comment: hidden when the author is banned or the post is gone, banned or taken down
    @Query("""
            SELECT new com.rizkysiregar.narrativ.model.response.CommentResponse(
                c.id, c.user.id, c.user.username, c.user.profileImage, c.content,
                c.moderationStatus, c.moderationReason, c.moderatedAt,
                c.parent.id, rt.id, ru.username, c.createdAt, 0L)
            FROM CommentEntity c LEFT JOIN c.replyTo rt LEFT JOIN rt.user ru
            WHERE c.parent.id = :parentId AND c.user.isDeleted = false AND c.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = c.user.id) OR (b.blockerId = c.user.id AND b.blockedId = :viewerId))
              AND c.post.isDeleted = false AND c.post.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND c.post.moderationStatus = com.rizkysiregar.narrativ.model.enums.ModerationStatus.ACTIVE
            ORDER BY c.createdAt ASC
            """)
    List<CommentResponse> findReplyResponses(@Param("parentId") UUID parentId, @Param("viewerId") UUID viewerId);

    // same visibility rules as findReplyResponses, paged
    @Query(value = """
            SELECT new com.rizkysiregar.narrativ.model.response.CommentResponse(
                c.id, c.user.id, c.user.username, c.user.profileImage, c.content,
                c.moderationStatus, c.moderationReason, c.moderatedAt,
                c.parent.id, rt.id, ru.username, c.createdAt, 0L)
            FROM CommentEntity c LEFT JOIN c.replyTo rt LEFT JOIN rt.user ru
            WHERE c.parent.id = :parentId AND c.user.isDeleted = false AND c.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = c.user.id) OR (b.blockerId = c.user.id AND b.blockedId = :viewerId))
              AND c.post.isDeleted = false AND c.post.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND c.post.moderationStatus = com.rizkysiregar.narrativ.model.enums.ModerationStatus.ACTIVE
            ORDER BY c.createdAt ASC, c.id ASC
            """, countQuery = """
            SELECT COUNT(c) FROM CommentEntity c
            WHERE c.parent.id = :parentId AND c.user.isDeleted = false AND c.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = c.user.id) OR (b.blockerId = c.user.id AND b.blockedId = :viewerId))
              AND c.post.isDeleted = false AND c.post.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND c.post.moderationStatus = com.rizkysiregar.narrativ.model.enums.ModerationStatus.ACTIVE
            """)
    Page<CommentResponse> findReplyResponsePage(@Param("parentId") UUID parentId, @Param("viewerId") UUID viewerId, Pageable pageable);

    // the author of the post a comment belongs to, used to decide who may delete it
    @Query("SELECT c.post.user.id FROM CommentEntity c WHERE c.id = :commentId")
    Optional<UUID> findPostOwnerId(@Param("commentId") UUID commentId);

    // all replies of a top-level comment in one statement, so they may reference each other
    @Modifying
    @Query("DELETE FROM CommentEntity c WHERE c.parent.id = :parentId")
    int deleteAllByParentId(@Param("parentId") UUID parentId);

    // replies that answered a deleted comment keep existing, they only lose the @tag
    @Modifying
    @Query("UPDATE CommentEntity c SET c.replyTo = NULL WHERE c.replyTo.id = :commentId")
    int clearReplyTo(@Param("commentId") UUID commentId);

    // raw count for the admin view
    long countByPostId(UUID postId);

    @Modifying
    @Query("DELETE FROM CommentEntity c WHERE c.post.id = :postId")
    int deleteAllByPostId(@Param("postId") UUID postId);

    // JPQL Refer to the entity name not the table name
    @Query("SELECT COUNT(c) FROM CommentEntity c WHERE c.post.id = :postId AND c.user.isDeleted = false AND c.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = c.user.id) OR (b.blockerId = c.user.id AND b.blockedId = :viewerId))")
    long contByPostId(@Param("postId") UUID postId, @Param("viewerId") UUID viewerId);

    Optional<CommentEntity> findByPostId(UUID postId);

    // a deleted account keeps no words behind; the rows stay so threads and reply counts do not break
    @Modifying
    @Query("UPDATE CommentEntity c SET c.content = :placeholder WHERE c.user.id = :userId")
    int overwriteContentByUserId(@Param("userId") UUID userId, @Param("placeholder") String placeholder);

    long countByUserId(UUID userId);
}
