package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.entity.UserDeviceEntity;
import com.rizkysiregar.feedzmore.model.request.RegisterDeviceRequest;
import com.rizkysiregar.feedzmore.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;

    @Transactional
    public void registerOrUpdatedDevice(UUID userId, RegisterDeviceRequest request) {
        UserDeviceEntity deviceEntity = userDeviceRepository
                .findByPushToken(request.getPushToken())
                .orElseGet(UserDeviceEntity::new);


        deviceEntity.setUserId(userId);
        deviceEntity.setDeviceId(request.getDeviceId());
        deviceEntity.setPlatform(request.getPlatform());
        deviceEntity.setPushToken(request.getPushToken());
        deviceEntity.setAppVersion(request.getAppVersion());
        deviceEntity.setOsVersion(request.getOsVersion());
        deviceEntity.setDeviceModel(request.getDeviceModel());
        deviceEntity.setLocale(request.getLocale());
        deviceEntity.setTimezone(request.getTimezone());
        deviceEntity.setActive(true);
        deviceEntity.setLastSeenAt(LocalDateTime.now());

        userDeviceRepository.save(deviceEntity);
    }

    @Transactional
    public void deactivateDevice(String pushToken) {
        userDeviceRepository.findByPushToken(pushToken)
                .ifPresent(device -> {
                    device.setActive(false);
                    device.setUpdateAt(LocalDateTime.now());
                    userDeviceRepository.save(device);
                });
    }

    /** Stops push for this token, but only when it belongs to the caller. */
    @Transactional
    public void deactivateDevice(UUID userId, String pushToken) {
        userDeviceRepository.findByPushToken(pushToken)
                .filter(device -> userId.equals(device.getUserId()))
                .ifPresent(device -> {
                    device.setActive(false);
                    device.setUpdateAt(LocalDateTime.now());
                    userDeviceRepository.save(device);
                });
    }

    @Transactional
    public void removeAllDevices(UUID userId) {
        userDeviceRepository.deleteByUserId(userId);
    }

    public List<UserDeviceEntity> getActiveDevice(UUID userId) {
        return userDeviceRepository.findByUserIdAndIsActiveTrue(userId);
    }

}
