package com.rizkysiregar.narrativ.service.chat;

import com.rizkysiregar.narrativ.config.MarpokatProperties;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatBlockSyncTest {

    private static final UUID A = UUID.fromString("0193a000-0000-7000-8000-000000000001");
    private static final UUID B = UUID.fromString("0193a000-0000-7000-8000-000000000002");

    @Mock
    private MarpokatClient marpokat;
    @Mock
    private UserBlockRepository userBlockRepository;

    private ChatBlockSync sync(boolean enabled) {
        MarpokatProperties props = enabled
                ? new MarpokatProperties("http://marpokat", "mpk_key", "mps_secret", Duration.ofHours(1))
                : new MarpokatProperties("", "", "", Duration.ofHours(1));
        return new ChatBlockSync(props, marpokat, userBlockRepository);
    }

    @Test
    void aBlockFreezesTheExistingDm() {
        when(userBlockRepository.existsBetween(A, B)).thenReturn(true);
        when(marpokat.findDirectMessage(A.toString(), B.toString())).thenReturn(Optional.of("chan-1"));

        sync(true).sync(A, B);

        verify(marpokat).setFrozen("chan-1", true);
    }

    @Test
    void liftingTheLastBlockUnfreezesIt() {
        when(userBlockRepository.existsBetween(A, B)).thenReturn(false);
        when(marpokat.findDirectMessage(A.toString(), B.toString())).thenReturn(Optional.of("chan-1"));

        sync(true).sync(A, B);

        verify(marpokat).setFrozen("chan-1", false);
    }

    @Test
    void aBlockInTheOtherDirectionKeepsItFrozen() {
        // A lifted theirs, but B's block is still there: existsBetween covers both directions
        when(userBlockRepository.existsBetween(A, B)).thenReturn(true);
        when(marpokat.findDirectMessage(A.toString(), B.toString())).thenReturn(Optional.of("chan-1"));

        sync(true).sync(A, B);

        verify(marpokat).setFrozen("chan-1", true);
        verify(marpokat, never()).setFrozen("chan-1", false);
    }

    @Test
    void withoutADmNothingIsFrozenAndNothingIsCreated() {
        when(userBlockRepository.existsBetween(A, B)).thenReturn(true);
        when(marpokat.findDirectMessage(anyString(), anyString())).thenReturn(Optional.empty());

        sync(true).sync(A, B);

        verify(marpokat, never()).setFrozen(anyString(), anyBoolean());
        verify(marpokat, never()).getOrCreateDirectMessage(anyString(), anyString());
    }

    @Test
    void marpokatBeingDownDoesNotBreakTheBlock() {
        when(userBlockRepository.existsBetween(A, B)).thenReturn(true);
        doThrow(new MarpokatClient.MarpokatException("down")).when(marpokat).findDirectMessage(anyString(), anyString());

        sync(true).sync(A, B); // must not throw: the block itself is already committed
    }

    @Test
    void doesNothingWhenChatIsNotConfigured() {
        sync(false).sync(A, B);

        verifyNoInteractions(marpokat, userBlockRepository);
    }
}
