package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface PostCrudRepository extends JpaRepository<PostEntity, UUID> {
    Optional<PostEntity> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByIdAndIsDeletedFalse(UUID id);
}
