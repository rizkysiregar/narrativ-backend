package com.rizkysiregar.narrativ.config;

import com.rizkysiregar.narrativ.model.response.WebPushConfigResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushPropertiesTest {

    @Test
    void enabledOnlyWhenTheFirebaseWebAppIsComplete() {
        WebPushProperties complete = new WebPushProperties("https://app.test/", "key", "proj", "123", "1:123:web:abc", "vapid");
        assertThat(complete.config()).isEqualTo(new WebPushConfigResponse(true, "key", "proj", "123", "1:123:web:abc", "vapid"));
        assertThat(complete.origin()).isEqualTo("https://app.test");

        WebPushProperties partial = new WebPushProperties("", "key", "proj", "", "", "");
        assertThat(partial.config()).isEqualTo(new WebPushConfigResponse(false, null, null, null, null, null));
    }
}
