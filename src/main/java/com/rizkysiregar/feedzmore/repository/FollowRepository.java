package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.FollowEntity;
import com.rizkysiregar.feedzmore.model.response.FollowUserResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FollowRepository extends JpaRepository<FollowEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO follows (id, follower_id, followed_id, created_at)
            VALUES (:id, :followerId, :followedId, CURRENT_TIMESTAMP)
            ON CONFLICT (follower_id, followed_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("followerId") UUID followerId,
                       @Param("followedId") UUID followedId);

    @Modifying
    @Query(value = "DELETE FROM follows WHERE follower_id = :followerId AND followed_id = :followedId", nativeQuery = true)
    int deleteByFollowerIdAndFollowedId(@Param("followerId") UUID followerId,
                                        @Param("followedId") UUID followedId);

    @Query("""
            SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM FollowEntity f
            WHERE f.follower.id = :followerId AND f.followed.id = :followedId
              AND f.follower.isDeleted = false AND f.followed.isDeleted = false
            """)
    boolean existsActiveFollow(@Param("followerId") UUID followerId, @Param("followedId") UUID followedId);

    @Query("""
            SELECT COUNT(f) FROM FollowEntity f
            WHERE f.followed.id = :followedId
              AND f.follower.isDeleted = false AND f.followed.isDeleted = false
            """)
    long countActiveFollowers(@Param("followedId") UUID followedId);

    @Query("""
            SELECT COUNT(f) FROM FollowEntity f
            WHERE f.follower.id = :followerId
              AND f.follower.isDeleted = false AND f.followed.isDeleted = false
            """)
    long countActiveFollowing(@Param("followerId") UUID followerId);

    @Query(value = """
            SELECT new com.rizkysiregar.feedzmore.model.response.FollowUserResponse(
                follower.id, follower.username, follower.firstName, follower.lastName, follower.profileImage)
            FROM FollowEntity f JOIN f.follower follower JOIN f.followed followed
            WHERE followed.id = :followedId AND follower.isDeleted = false AND followed.isDeleted = false
            ORDER BY follower.id ASC
            """, countQuery = """
            SELECT COUNT(f) FROM FollowEntity f JOIN f.follower follower JOIN f.followed followed
            WHERE followed.id = :followedId AND follower.isDeleted = false AND followed.isDeleted = false
            """)
    Page<FollowUserResponse> findActiveFollowers(@Param("followedId") UUID followedId, Pageable pageable);

    @Query(value = """
            SELECT new com.rizkysiregar.feedzmore.model.response.FollowUserResponse(
                followed.id, followed.username, followed.firstName, followed.lastName, followed.profileImage)
            FROM FollowEntity f JOIN f.follower follower JOIN f.followed followed
            WHERE follower.id = :followerId AND follower.isDeleted = false AND followed.isDeleted = false
            ORDER BY followed.id ASC
            """, countQuery = """
            SELECT COUNT(f) FROM FollowEntity f JOIN f.follower follower JOIN f.followed followed
            WHERE follower.id = :followerId AND follower.isDeleted = false AND followed.isDeleted = false
            """)
    Page<FollowUserResponse> findActiveFollowing(@Param("followerId") UUID followerId, Pageable pageable);

    @Query("""
            SELECT follower.id FROM FollowEntity f JOIN f.follower follower JOIN f.followed followed
            WHERE followed.id = :followedId AND follower.isDeleted = false AND followed.isDeleted = false
            ORDER BY follower.id ASC
            """)
    List<UUID> findActiveFollowerIdsByFollowedId(@Param("followedId") UUID followedId, Pageable pageable);

    @Query("""
            SELECT follower.id FROM FollowEntity f JOIN f.follower follower JOIN f.followed followed
            WHERE followed.id = :followedId AND follower.isDeleted = false AND followed.isDeleted = false
              AND follower.id > :afterFollowerId
            ORDER BY follower.id ASC
            """)
    List<UUID> findActiveFollowerIdsByFollowedIdAfter(@Param("followedId") UUID followedId,
                                                       @Param("afterFollowerId") UUID afterFollowerId, Pageable pageable);
}
