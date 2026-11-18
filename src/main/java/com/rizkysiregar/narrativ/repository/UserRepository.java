package com.rizkysiregar.narrativ.repository;



import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
