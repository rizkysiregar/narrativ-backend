package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.constant.Constants;
import com.rizkysiregar.narrativ.model.response.ClientConfigResponse;
import com.rizkysiregar.narrativ.service.FeatureFlagService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientConfigControllerTest {

    @Test
    void clientConfig_ReportsTheChatSwitch() {
        FeatureFlagService flags = mock(FeatureFlagService.class);
        when(flags.isChatEnabled()).thenReturn(false);

        assertThat(new ClientConfigController(flags).clientConfig())
                .isEqualTo(new ClientConfigResponse(new ClientConfigResponse.Features(false)));
    }

    @Test
    void clientConfig_IsReachableWithoutLogin() {
        assertThat(Constants.JWT_WHITELIST).contains("/config/client");
    }
}
