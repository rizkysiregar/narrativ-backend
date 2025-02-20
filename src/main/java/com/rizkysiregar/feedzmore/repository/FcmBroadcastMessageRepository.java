package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.FcmBroadcastEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FcmBroadcastMessageRepository extends JpaRepository<FcmBroadcastEntity, UUID> {
    Optional<FcmBroadcastEntity> findByMessageName(String messageName);
}
