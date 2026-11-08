package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.dto.PostCountDTO;
import com.rizkysiregar.feedzmore.model.entity.LikeEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LikeRepository extends JpaRepository<LikeEntity, UUID> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    long countByPostId(UUID postId);

    @Query("""
            SELECT new com.rizkysiregar.feedzmore.model.dto.PostCountDTO(l.post.id, COUNT(l))
            FROM LikeEntity l WHERE l.post.id IN :postIds GROUP BY l.post.id
            """)
    List<PostCountDTO> countByPostIds(@Param("postIds") List<UUID> postIds);

    @Query("""
            SELECT DISTINCT l.post.id FROM LikeEntity l
            WHERE l.user.id = :userId AND l.post.id IN :postIds
            """)
    List<UUID> findLikedPostIds(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);

    Optional<LikeEntity> findByUserIdAndPostId(UUID userId, UUID postId);

    @Modifying
    @Query("DELETE FROM LikeEntity l WHERE l.post.id = :postId")
    int deleteAllByPostId(@Param("postId") UUID postId);

    @Modifying
    @Query("DELETE FROM LikeEntity l WHERE l.user.id = :userId AND l.post.id = :postId")
    int deleteDirectly(UUID userId, UUID postId);

    UUID user(UserEntity user);

    UUID post(PostEntity post);
}
