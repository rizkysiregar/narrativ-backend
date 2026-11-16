package com.rizkysiregar.narrativ.repository;

import com.rizkysiregar.narrativ.model.entity.FcmBroadcastEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FcmBroadcastMessageRepository extends JpaRepository<FcmBroadcastEntity, UUID> {
    Optional<FcmBroadcastEntity> findByMessageName(String messageName);
}
