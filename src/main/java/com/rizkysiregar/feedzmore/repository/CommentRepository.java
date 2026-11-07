package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.dto.PostCountDTO;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.response.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {
    @Query(value = """
            SELECT new com.rizkysiregar.feedzmore.model.response.CommentResponse(
                c.user.username, c.user.profileImage, c.content)
            FROM CommentEntity c
            WHERE c.post.id = :postId AND c.parent IS NULL AND c.user.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE
            ORDER BY c.createdAt ASC, c.id ASC
            """, countQuery = """
            SELECT COUNT(c) FROM CommentEntity c
            WHERE c.post.id = :postId AND c.parent IS NULL AND c.user.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE
            """)
    Page<CommentResponse> findTopLevelCommentResponses(@Param("postId") UUID postId, Pageable pageable);

    @Query("""
            SELECT new com.rizkysiregar.feedzmore.model.dto.PostCountDTO(c.post.id, COUNT(c))
            FROM CommentEntity c WHERE c.post.id IN :postIds AND c.user.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE GROUP BY c.post.id
            """)
    List<PostCountDTO> countByPostIds(@Param("postIds") List<UUID> postIds);

    // fetch replies comment, hiding the ones written by banned users
    @Query("""
            SELECT c FROM CommentEntity c
            WHERE c.parent.id = :parentId AND c.user.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE
            ORDER BY c.createdAt ASC
            """)
    List<CommentEntity> findByParentIdOrderByCreatedAtAsc(@Param("parentId") UUID parentId);

    // JPQL Refer to the entity name not the table name
    @Query("SELECT COUNT(c) FROM CommentEntity c WHERE c.post.id = :postId AND c.user.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE")
    long contByPostId(@Param("postId") UUID postId);

    Optional<CommentEntity> findByPostId(UUID postId);

    long countByUserId(UUID userId);
}
