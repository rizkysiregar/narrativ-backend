package com.rizkysiregar.feedzmore.config;

import com.rizkysiregar.feedzmore.service.appcheck.AppCheckTokenVerifier;
import com.rizkysiregar.feedzmore.service.appcheck.JwksAppCheckTokenVerifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppCheckProperties.class)
public class AppCheckConfig {

    @Bean
    public AppCheckTokenVerifier appCheckTokenVerifier(AppCheckProperties properties) {
        if (properties.mode() == AppCheckProperties.Mode.OFF) {
            // never asked while off; there is also no project number to build a real one with
            return token -> {
                throw new IllegalStateException("App Check is off");
            };
        }
        if (properties.projectNumber().isBlank()) {
            throw new IllegalStateException(
                    "app.appcheck.mode is " + properties.mode() + " but APP_APPCHECK_PROJECT_NUMBER is not set. "
                            + "It is the project number (digits) of the Firebase project, not its id.");
        }
        return JwksAppCheckTokenVerifier.create(properties.projectNumber());
    }
}
