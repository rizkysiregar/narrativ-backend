package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, UUID> {
    Optional<UserDeviceEntity> findByPushToken(String pushToken);
    List<UserDeviceEntity> findByUserIdAndIsActiveTrue(UUID userId);
    void deleteByPushToken(String pushToken);
}
