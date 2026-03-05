package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.LikeEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LikeRepository extends JpaRepository<LikeEntity, UUID> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    long countByPostId(UUID postId);

    Optional<LikeEntity> findByUserIdAndPostId(UUID userId, UUID postId);

    @Modifying
    @Query("DELETE FROM LikeEntity l WHERE l.user.id = :userId AND l.post.id = :postId")
    int deleteDirectly(UUID userId, UUID postId);

    UUID user(UserEntity user);

    UUID post(PostEntity post);
}
