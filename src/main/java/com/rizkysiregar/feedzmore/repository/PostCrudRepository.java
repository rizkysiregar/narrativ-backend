package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface PostCrudRepository extends JpaRepository<PostEntity, UUID> {
    Optional<PostEntity> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByIdAndIsDeletedFalse(UUID id);

    long countByUserIdAndIsDeletedFalse(UUID userId);

    // a post is visible to other users while it is not deleted and its author is not banned
    @Query("SELECT p FROM PostEntity p WHERE p.id = :id AND p.isDeleted = false AND p.user.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE")
    Optional<PostEntity> findVisibleById(@Param("id") UUID id);

    @Query("SELECT COUNT(p) > 0 FROM PostEntity p WHERE p.id = :id AND p.isDeleted = false AND p.user.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE")
    boolean existsVisibleById(@Param("id") UUID id);
}
