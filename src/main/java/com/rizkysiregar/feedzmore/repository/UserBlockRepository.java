package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.UserBlockEntity;
import com.rizkysiregar.feedzmore.model.response.FollowUserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlockEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO user_blocks (id, blocker_id, blocked_id, created_at)
            VALUES (:id, :blockerId, :blockedId, CURRENT_TIMESTAMP)
            ON CONFLICT (blocker_id, blocked_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId);

    @Modifying
    @Query("DELETE FROM UserBlockEntity b WHERE b.blockerId = :blockerId AND b.blockedId = :blockedId")
    int deleteByPair(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId);

    @Modifying
    @Query("DELETE FROM UserBlockEntity b WHERE b.blockerId = :userId OR b.blockedId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    /** Whether either of the two blocked the other. */
    @Query("""
            SELECT COUNT(b) > 0 FROM UserBlockEntity b
            WHERE (b.blockerId = :a AND b.blockedId = :b) OR (b.blockerId = :b AND b.blockedId = :a)
            """)
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);

    /** Everyone the user blocked or was blocked by, for filters that cannot use a subquery. */
    @Query("""
            SELECT CASE WHEN b.blockerId = :userId THEN b.blockedId ELSE b.blockerId END
            FROM UserBlockEntity b WHERE b.blockerId = :userId OR b.blockedId = :userId
            """)
    List<UUID> findRelatedUserIds(@Param("userId") UUID userId);

    /** The users {@code blockerId} blocked, most recent first; deleted and banned accounts are left out. */
    @Query(value = """
            SELECT new com.rizkysiregar.feedzmore.model.response.FollowUserResponse(
                u.id, u.username, u.firstName, u.lastName, u.profileImage)
            FROM UserBlockEntity b JOIN users u ON u.id = b.blockedId
            WHERE b.blockerId = :blockerId AND u.isDeleted = false
              AND u.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE
            ORDER BY b.createdAt DESC, b.id DESC
            """, countQuery = """
            SELECT COUNT(b) FROM UserBlockEntity b JOIN users u ON u.id = b.blockedId
            WHERE b.blockerId = :blockerId AND u.isDeleted = false
              AND u.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE
            """)
    Page<FollowUserResponse> findBlockedUsers(@Param("blockerId") UUID blockerId, Pageable pageable);
}
