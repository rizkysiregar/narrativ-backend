package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {
    List<CommentEntity> findByPostIdAndParentIsNullOrderByCreatedAtAsc(UUID postId, Pageable pageable); // fetch top-level comments

    List<CommentEntity> findByParentIdOrderByCreatedAtAsc(UUID parentId); // fetch replies comment

}
