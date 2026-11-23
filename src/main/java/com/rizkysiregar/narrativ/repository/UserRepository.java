package com.rizkysiregar.narrativ.repository;



import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {
    // usernames and emails are unique regardless of case (see V13), so every lookup ignores it too
    @Query("SELECT u FROM users u WHERE lower(u.username) = lower(:username)")
    Optional<UserEntity> findByUsername(@Param("username") String username);

    // "active" = not deleted by the user and not banned by an admin
    @Query("SELECT u FROM users u WHERE lower(u.username) = lower(:username) AND u.isDeleted = false AND u.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE")
    Optional<UserEntity> findActiveByUsername(@Param("username") String username);

    Optional<UserEntity> findByGoogleId(String googleId);

    /** Which of the given object keys are still somebody's profile picture (deleted and banned users included). */
    @Query("SELECT u.profileImage FROM users u WHERE u.profileImage IN :keys")
    List<String> findReferencedProfileImages(@Param("keys") Collection<String> keys);

    @Query("SELECT u FROM users u WHERE lower(u.email) = lower(:email)")
    Optional<UserEntity> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM users u WHERE u.id = :id AND u.isDeleted = false AND u.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE")
    Optional<UserEntity> findActiveById(@Param("id") UUID id);

    long countByRoleAndIsDeletedFalse(Role role);
    // is the username / email held by any user other than this one
    @Query("SELECT COUNT(u) > 0 FROM users u WHERE lower(u.username) = lower(:username) AND u.id <> :id")
    boolean existsByUsernameAndIdNot(@Param("username") String username, @Param("id") UUID id);

    @Query("SELECT COUNT(u) > 0 FROM users u WHERE lower(u.email) = lower(:email) AND u.id <> :id")
    boolean existsByEmailAndIdNot(@Param("email") String email, @Param("id") UUID id);

    /**
     * Active users whose username or name contains the term, without the people on either side of a block with
     * the viewer. Best match first: the exact username, then usernames and names that start with the term, then
     * the rest; shorter usernames first within each. The name expression is the one idx_users_full_name_trgm (V21)
     * is built on. The patterns come from SearchTerm, which escapes %, _ and ! with a ! (not a backslash: Hibernate's
     * parameter parser reads a backslash in native SQL as an escape).
     */
    @Query(nativeQuery = true, value = """
            SELECT u.* FROM users u
            WHERE u.is_deleted = false AND u.status = 'ACTIVE'
              AND (lower(u.username) LIKE :contains ESCAPE '!'
                   OR lower(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) LIKE :contains ESCAPE '!')
              AND NOT EXISTS (SELECT 1 FROM user_blocks b
                              WHERE (b.blocker_id = :viewerId AND b.blocked_id = u.id)
                                 OR (b.blocker_id = u.id AND b.blocked_id = :viewerId))
            ORDER BY CASE WHEN lower(u.username) = :term THEN 0
                          WHEN lower(u.username) LIKE :prefix ESCAPE '!' THEN 1
                          WHEN lower(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) LIKE :prefix ESCAPE '!' THEN 2
                          ELSE 3 END,
                     length(u.username), lower(u.username), u.id
            """, countQuery = """
            SELECT COUNT(*) FROM users u
            WHERE u.is_deleted = false AND u.status = 'ACTIVE'
              AND (lower(u.username) LIKE :contains ESCAPE '!'
                   OR lower(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) LIKE :contains ESCAPE '!')
              AND NOT EXISTS (SELECT 1 FROM user_blocks b
                              WHERE (b.blocker_id = :viewerId AND b.blocked_id = u.id)
                                 OR (b.blocker_id = u.id AND b.blocked_id = :viewerId))
            """)
    Page<UserEntity> searchActive(@Param("term") String term, @Param("contains") String contains,
                                  @Param("prefix") String prefix, @Param("viewerId") UUID viewerId, Pageable pageable);
}
