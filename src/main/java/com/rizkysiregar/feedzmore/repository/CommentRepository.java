package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {
    List<CommentEntity> findByPostIdAndParentIsNullOrderByCreatedAtAsc(UUID postId, Pageable pageable); // fetch top-level comments

    List<CommentEntity> findByParentIdOrderByCreatedAtAsc(UUID parentId); // fetch replies comment

    // JPQL Refer to the entity name not the table name
    @Query("SELECT COUNT(c) FROM CommentEntity c WHERE c.post.id = :postId")
    long contByPostId(@Param("postId") UUID postId);
}
