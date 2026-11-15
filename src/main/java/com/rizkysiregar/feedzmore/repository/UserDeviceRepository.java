package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, UUID> {
    Optional<UserDeviceEntity> findByPushToken(String pushToken);
    List<UserDeviceEntity> findByUserIdAndIsActiveTrue(UUID userId);
    /** The device the user used last, active or not: the best hint of their language when no request is around. */
    Optional<UserDeviceEntity> findFirstByUserIdOrderByUpdateAtDesc(UUID userId);
    void deleteByPushToken(String pushToken);

    @Modifying
    @Query("DELETE FROM UserDeviceEntity d WHERE d.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
