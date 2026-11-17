package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.repository.FollowRepository;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.util.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private ProfileImageUrlResolver profileImageUrlResolver;

    @InjectMocks
    private UserBlockService service;

    private final UUID blockerId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @Test
    void blockStoresTheBlockAndEndsFollowsInBothDirections() {
        when(userRepository.findActiveById(targetId)).thenReturn(Optional.of(new UserEntity()));

        assertEquals(Operation.CREATED, service.block(blockerId, targetId));

        verify(userBlockRepository).insertIfAbsent(any(UUID.class), eq(blockerId), eq(targetId));
        verify(followRepository).deleteByFollowerIdAndFollowedId(blockerId, targetId);
        verify(followRepository).deleteByFollowerIdAndFollowedId(targetId, blockerId);
    }

    @Test
    void cannotBlockYourself() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.block(blockerId, blockerId));

        assertEquals(GlobalMessage.CANNOT_BLOCK_SELF.getMessage(), e.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
        verifyNoInteractions(userBlockRepository, followRepository);
    }

    @Test
    void cannotBlockAnInactiveOrUnknownUser() {
        when(userRepository.findActiveById(targetId)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.block(blockerId, targetId));

        assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
        verify(userBlockRepository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void unblockOnlyRemovesTheCallersOwnBlock() {
        assertEquals(Operation.DELETED, service.unblock(blockerId, targetId));

        verify(userBlockRepository).deleteByPair(blockerId, targetId);
    }
}
