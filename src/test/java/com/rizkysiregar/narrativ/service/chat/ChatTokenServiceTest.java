package com.rizkysiregar.narrativ.service.chat;

import com.rizkysiregar.narrativ.config.MarpokatProperties;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.response.ChatTokenResponse;
import com.rizkysiregar.narrativ.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatTokenServiceTest {

    private static final String SECRET = "mps_test-secret-test-secret-test-secret-abcdefg";
    private static final Instant NOW = Instant.parse("2026-11-22T08:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private MarpokatClient marpokat;

    private final MarpokatProperties enabled = new MarpokatProperties(
            "https://api-dev-marpokat.example.com/", "mpk_key", SECRET, Duration.ofHours(1));

    private static UserEntity user(String first, String last, String image) {
        UserEntity u = new UserEntity();
        u.setId(UUID.fromString("0193a000-0000-7000-8000-000000000001"));
        u.setUsername("rizky");
        u.setFirstName(first);
        u.setLastName(last);
        u.setProfileImage(image);
        return u;
    }

    @Test
    void issueToken_UpsertsTheUserThenSignsAToken() {
        UserEntity u = user("Rizky", "Siregar", "https://lh3.googleusercontent.com/a/photo");
        when(userRepository.findActiveById(u.getId())).thenReturn(Optional.of(u));
        MarpokatClient real = new MarpokatClient(enabled, Clock.fixed(NOW, ZoneOffset.UTC));
        when(marpokat.userToken(u.getId().toString())).thenReturn(real.userToken(u.getId().toString()));

        ChatTokenResponse res = new ChatTokenService(enabled, marpokat, userRepository).issueToken(u.getId());

        ArgumentCaptor<MarpokatClient.UserProfile> profile = ArgumentCaptor.forClass(MarpokatClient.UserProfile.class);
        verify(marpokat).upsertUser(eq(u.getId().toString()), profile.capture());
        assertThat(profile.getValue().name()).isEqualTo("Rizky Siregar");
        assertThat(profile.getValue().avatarUrl()).isEqualTo("https://lh3.googleusercontent.com/a/photo");
        assertThat(profile.getValue().metadata()).containsEntry("username", "rizky");

        assertThat(res.apiKey()).isEqualTo("mpk_key");
        assertThat(res.baseUrl()).isEqualTo("https://api-dev-marpokat.example.com");
        assertThat(res.userId()).isEqualTo(u.getId().toString());
        assertThat(res.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> java.util.Date.from(NOW))
                .build().parseSignedClaims(res.token()).getPayload();
        assertThat(claims.getSubject()).isEqualTo(u.getId().toString());
        assertThat(claims.get("server")).isNull();
    }

    @Test
    void profileOf_UploadedPictureIsNotSentBecauseItsUrlExpires() {
        MarpokatClient.UserProfile p = ChatTokenService.profileOf(user("Rizky", null, "profile/abc.jpg"));
        assertThat(p.avatarUrl()).isNull();
        assertThat(p.name()).isEqualTo("Rizky");
    }

    @Test
    void profileOf_NoNameFallsBackToUsername() {
        assertThat(ChatTokenService.profileOf(user(" ", null, null)).name()).isEqualTo("rizky");
    }

    @Test
    void issueToken_ChatNotConfigured_Is503() {
        MarpokatProperties off = new MarpokatProperties("", "", "", Duration.ofHours(1));

        assertThatThrownBy(() -> new ChatTokenService(off, marpokat, userRepository).issueToken(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getGlobalMessage()).isEqualTo(GlobalMessage.CHAT_UNAVAILABLE);
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        verifyNoInteractions(marpokat, userRepository);
    }

    @Test
    void issueToken_MarpokatDown_Is503AndNoTokenIsSigned() {
        UserEntity u = user("Rizky", "Siregar", null);
        when(userRepository.findActiveById(u.getId())).thenReturn(Optional.of(u));
        doThrow(new MarpokatClient.MarpokatException("connection refused")).when(marpokat).upsertUser(any(), any());

        assertThatThrownBy(() -> new ChatTokenService(enabled, marpokat, userRepository).issueToken(u.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(marpokat, org.mockito.Mockito.never()).userToken(any());
    }

    @Test
    void issueToken_UnknownUser_Is404() {
        UUID id = UUID.randomUUID();
        when(userRepository.findActiveById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ChatTokenService(enabled, marpokat, userRepository).issueToken(id))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(marpokat);
    }
}
