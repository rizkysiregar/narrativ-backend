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
    Optional<UserEntity> findByUsername(String username);

    // "active" = not deleted by the user and not banned by an admin
    @Query("SELECT u FROM users u WHERE u.username = :username AND u.isDeleted = false AND u.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE")
    Optional<UserEntity> findActiveByUsername(@Param("username") String username);
    Optional<UserEntity> findByEmail(String email);

    @Query("SELECT u FROM users u WHERE u.id = :id AND u.isDeleted = false AND u.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE")
    Optional<UserEntity> findActiveById(@Param("id") UUID id);

    long countByRoleAndIsDeletedFalse(Role role);
    boolean existsByUsernameAndIdNot(String username, UUID id); // Check if there is any user in the database who has this username, but does NOT have this ID
    boolean existsByEmailAndIdNot(String email, UUID id);
}
