package com.rizkysiregar.narrativ.repository;

import com.rizkysiregar.narrativ.model.entity.NotificationBroadcastEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationBroadcastRepository extends JpaRepository<NotificationBroadcastEntity, UUID>, JpaSpecificationExecutor<NotificationBroadcastEntity> {
}
