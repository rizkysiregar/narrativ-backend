package com.rizkysiregar.narrativ.service.chat;

import com.rizkysiregar.narrativ.config.MarpokatProperties;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.response.DirectMessageResponse;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatDirectMessageServiceTest {

    private static final UUID ME = UUID.fromString("0193a000-0000-7000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("0193a000-0000-7000-8000-000000000002");

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private MarpokatClient marpokat;

    private final MarpokatProperties enabled = new MarpokatProperties(
            "https://marpokat.example.com", "mpk_key", "mps_secret-secret-secret-secret-secret-1234", Duration.ofHours(1));

    private ChatDirectMessageService service(MarpokatProperties properties) {
        return new ChatDirectMessageService(properties, marpokat, userRepository, userBlockRepository);
    }

    private static UserEntity user(UUID id, String username) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setFirstName(username);
        return u;
    }

    private void bothUsersExist() {
        when(userRepository.findActiveById(ME)).thenReturn(Optional.of(user(ME, "me")));
        when(userRepository.findActiveById(OTHER)).thenReturn(Optional.of(user(OTHER, "other")));
    }

    @Test
    void open_UpsertsBothUsersThenCreatesTheChannel() {
        bothUsersExist();
        when(userBlockRepository.existsBetween(ME, OTHER)).thenReturn(false);
        when(marpokat.getOrCreateDirectMessage(ME.toString(), OTHER.toString())).thenReturn("chan-1");

        DirectMessageResponse res = service(enabled).open(ME, OTHER);

        assertThat(res.channelId()).isEqualTo("chan-1");
        InOrder order = inOrder(marpokat);
        order.verify(marpokat).upsertUser(org.mockito.ArgumentMatchers.eq(ME.toString()), any());
        order.verify(marpokat).upsertUser(org.mockito.ArgumentMatchers.eq(OTHER.toString()), any());
        order.verify(marpokat).getOrCreateDirectMessage(ME.toString(), OTHER.toString());
    }

    @Test
    void open_Yourself_Is400() {
        assertThatThrownBy(() -> service(enabled).open(ME, ME))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(marpokat, userRepository);
    }

    @Test
    void open_BlockedEitherWay_LooksLikeAnUnknownUserAndTouchesNothing() {
        bothUsersExist();
        when(userBlockRepository.existsBetween(ME, OTHER)).thenReturn(true);

        assertThatThrownBy(() -> service(enabled).open(ME, OTHER))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getGlobalMessage()).isEqualTo(GlobalMessage.USER_NOT_FOUND);
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verifyNoInteractions(marpokat);
    }

    @Test
    void open_UnknownOrDeletedTarget_Is404() {
        when(userRepository.findActiveById(ME)).thenReturn(Optional.of(user(ME, "me")));
        when(userRepository.findActiveById(OTHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(enabled).open(ME, OTHER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(marpokat);
    }

    @Test
    void open_ChatNotConfigured_Is503() {
        MarpokatProperties off = new MarpokatProperties("", "", "", Duration.ofHours(1));

        assertThatThrownBy(() -> service(off).open(ME, OTHER))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getGlobalMessage()).isEqualTo(GlobalMessage.CHAT_UNAVAILABLE);
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        verifyNoInteractions(marpokat, userRepository);
    }

    @Test
    void open_MarpokatDown_Is503() {
        bothUsersExist();
        when(userBlockRepository.existsBetween(ME, OTHER)).thenReturn(false);
        doThrow(new MarpokatClient.MarpokatException("connection refused")).when(marpokat).upsertUser(any(), any());

        assertThatThrownBy(() -> service(enabled).open(ME, OTHER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(marpokat, never()).getOrCreateDirectMessage(any(), any());
    }
}
