package com.rizkysiregar.feedzmore.repository;



import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByIdAndIsDeletedFalse(UUID id);
    boolean existsByUsernameAndIdNot(String username, UUID id); // Check if there is any user in the database who has this username, but does NOT have this ID
    boolean existsByEmailAndIdNot(String email, UUID id);
}
