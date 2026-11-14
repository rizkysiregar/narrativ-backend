package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.entity.UserDeviceEntity;
import com.rizkysiregar.feedzmore.repository.UserDeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeviceServiceTest {

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @InjectMocks
    private UserDeviceService service;

    private final UUID userId = UUID.randomUUID();

    private UserDeviceEntity device(UUID owner) {
        UserDeviceEntity device = new UserDeviceEntity();
        device.setUserId(owner);
        device.setPushToken("token");
        device.setActive(true);
        return device;
    }

    @Test
    void deactivatesTheCallersOwnDevice() {
        UserDeviceEntity device = device(userId);
        when(userDeviceRepository.findByPushToken("token")).thenReturn(Optional.of(device));

        service.deactivateDevice(userId, "token");

        assertFalse(device.isActive());
        verify(userDeviceRepository).save(device);
    }

    @Test
    void ignoresATokenThatBelongsToSomeoneElse() {
        UserDeviceEntity device = device(UUID.randomUUID());
        when(userDeviceRepository.findByPushToken("token")).thenReturn(Optional.of(device));

        service.deactivateDevice(userId, "token");

        assertTrue(device.isActive());
        verify(userDeviceRepository, never()).save(device);
    }

    @Test
    void ignoresAnUnknownToken() {
        when(userDeviceRepository.findByPushToken("token")).thenReturn(Optional.empty());

        service.deactivateDevice(userId, "token");

        verify(userDeviceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
