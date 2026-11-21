package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.constant.Constants;
import com.rizkysiregar.narrativ.model.response.GoogleSignInConfigResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleSignInConfigControllerTest {

    @Test
    void googleConfig_ClientIdSet_IsEnabled() {
        GoogleSignInConfigResponse config = new GoogleSignInConfigController(" web-client.apps.googleusercontent.com ")
                .googleConfig();

        assertThat(config.enabled()).isTrue();
        assertThat(config.clientId()).isEqualTo("web-client.apps.googleusercontent.com");
    }

    @Test
    void googleConfig_NoClientId_IsDisabled() {
        assertThat(new GoogleSignInConfigController("").googleConfig())
                .isEqualTo(new GoogleSignInConfigResponse(false, null));
        assertThat(new GoogleSignInConfigController(null).googleConfig().enabled()).isFalse();
    }

    @Test
    void googleConfig_IsReachableWithoutLogin() {
        // the login page asks for it before anyone is signed in
        assertThat(Constants.JWT_WHITELIST).contains("/auth/google-config");
    }
}
