package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.ParameterEntity;
import com.rizkysiregar.narrativ.repository.ParameterRepository;
import com.rizkysiregar.narrativ.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private ParameterRepository parameterRepository;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private FeatureFlagService service;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static ParameterEntity flag(String key, String value) {
        return new ParameterEntity(key, value);
    }

    @Test
    void isChatEnabled_cacheMiss_readsTheDatabaseAndCachesTheSwitches() {
        when(parameterRepository.findByParamKeyStartingWith("feature.")).thenReturn(List.of(flag("feature.chat.enabled", " true ")));

        assertThat(service.isChatEnabled()).isTrue();

        verify(redisUtil).set(FeatureFlagService.CACHE_KEY, Map.of("feature.chat.enabled", true),
                FeatureFlagService.CACHE_TTL.toSeconds());
    }

    @Test
    void isChatEnabled_cacheHit_skipsTheDatabase() {
        when(redisUtil.get(FeatureFlagService.CACHE_KEY)).thenReturn(Map.of("feature.chat.enabled", false));

        assertThat(service.isChatEnabled()).isFalse();

        verifyNoInteractions(parameterRepository);
    }

    @Test
    void isChatEnabled_missingOrUnknownValue_isOff() {
        when(parameterRepository.findByParamKeyStartingWith("feature.")).thenReturn(List.of());
        assertThat(service.isChatEnabled()).isFalse();

        when(parameterRepository.findByParamKeyStartingWith("feature.")).thenReturn(List.of(flag("feature.chat.enabled", "yes")));
        assertThat(service.isChatEnabled()).isFalse();
    }

    @Test
    void isChatEnabled_redisDown_fallsBackToTheDatabase() {
        when(redisUtil.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        doThrow(new IllegalStateException("redis down")).when(redisUtil).set(anyString(), eq(Map.of("feature.chat.enabled", true)), eq(600L));
        when(parameterRepository.findByParamKeyStartingWith("feature.")).thenReturn(List.of(flag("feature.chat.enabled", "true")));

        assertThat(service.isChatEnabled()).isTrue();
    }

    @Test
    void requireChatEnabled_off_isForbidden() {
        when(redisUtil.get(FeatureFlagService.CACHE_KEY)).thenReturn(Map.of("feature.chat.enabled", false));

        assertThatThrownBy(service::requireChatEnabled)
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GlobalMessage.CHAT_DISABLED.name());
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void requireChatEnabled_on_passes() {
        when(redisUtil.get(FeatureFlagService.CACHE_KEY)).thenReturn(Map.of("feature.chat.enabled", true));

        assertThatCode(service::requireChatEnabled).doesNotThrowAnyException();
    }

    @Test
    void evictAfterCommit_insideATransaction_waitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.evictAfterCommit();

        verify(redisUtil, never()).delete(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(redisUtil).delete(FeatureFlagService.CACHE_KEY);
    }

    @Test
    void evictAfterCommit_withoutATransaction_dropsRightAway() {
        service.evictAfterCommit();

        verify(redisUtil).delete(FeatureFlagService.CACHE_KEY);
    }

    @Test
    void normalizeValue_onlyChecksFeatureSwitches() {
        assertThat(FeatureFlagService.normalizeValue("feature.chat.enabled", "FALSE")).isEqualTo("false");
        assertThat(FeatureFlagService.normalizeValue("feed.ranking.gravity", "1.5")).isEqualTo("1.5");
        assertThatThrownBy(() -> FeatureFlagService.normalizeValue("feature.chat.enabled", "on"))
                .isInstanceOf(BusinessException.class);
    }
}
