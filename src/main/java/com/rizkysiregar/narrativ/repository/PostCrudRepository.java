package com.rizkysiregar.narrativ.repository;

import com.rizkysiregar.narrativ.model.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface PostCrudRepository extends JpaRepository<PostEntity, UUID>, JpaSpecificationExecutor<PostEntity> {
    Optional<PostEntity> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByIdAndIsDeletedFalse(UUID id);

    @Modifying
    @Query("UPDATE PostEntity p SET p.isDeleted = true WHERE p.user.id = :userId AND p.isDeleted = false")
    int softDeleteAllByUserId(@Param("userId") UUID userId);

    long countByUserIdAndIsDeletedFalse(UUID userId);

    // other users can interact with a post (like, comment) while it is not deleted, its author is not banned
    // and it is not taken down; a taken down post is still listed (with a status) but nothing new can be added to it
    @Query("SELECT p FROM PostEntity p WHERE p.id = :id AND p.isDeleted = false AND p.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND p.moderationStatus = com.rizkysiregar.narrativ.model.enums.ModerationStatus.ACTIVE")
    Optional<PostEntity> findInteractableById(@Param("id") UUID id);

    @Query("SELECT COUNT(p) > 0 FROM PostEntity p WHERE p.id = :id AND p.isDeleted = false AND p.user.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND p.moderationStatus = com.rizkysiregar.narrativ.model.enums.ModerationStatus.ACTIVE")
    boolean existsInteractableById(@Param("id") UUID id);
}
