package com.rizkysiregar.feedzmore.config;

import com.rizkysiregar.feedzmore.config.AppCheckProperties.Mode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the rules shipped in application.yml: what the mobile app calls is covered, and what the web admin
 * or the public pages call is not, because those cannot send an App Check token.
 */
class AppCheckRulesTest {

    private static AppCheckProperties properties;

    @BeforeAll
    static void load() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        properties = Binder.get(environment).bind("app.appcheck", AppCheckProperties.class).get();
    }

    @Test
    void isOffUnlessAnEnvironmentTurnsItOn() {
        assertEquals(Mode.OFF, properties.mode());
    }

    @Test
    void theAnonymousAuthEndpointsOfTheMobileAppAreCovered() {
        for (String path : new String[]{"/auth/register", "/auth/register/otp", "/auth/register/verify-otp",
                "/auth/login", "/auth/google", "/auth/forgot-password", "/auth/verify-otp", "/auth/reset-password"}) {
            assertTrue(properties.ruleFor(path).isPresent(), path + " should be covered");
            assertEquals(null, properties.ruleFor(path).get().mode(), path + " follows the global mode");
        }
    }

    @Test
    void signedInEndpointsAreOnlyObserved() {
        for (String path : new String[]{"/users/me", "/posts/upload-url", "/reports", "/notifications", "/devices", "/files/x"}) {
            assertTrue(properties.ruleFor(path).isPresent(), path + " should be covered");
            assertEquals(Mode.LOG, properties.ruleFor(path).get().mode(), path + " must not be enforced yet");
        }
    }

    @Test
    void adminPublicAndInfrastructurePathsAreNeverCovered() {
        for (String path : new String[]{"/admin/users", "/admin/metrics", "/auth/admin/login", "/auth/register-admin",
                "/auth/refresh", "/auth/logout", "/legal/privacy-policy", "/res/photo", "/actuator/health",
                "/v3/api-docs", "/swagger-ui/index.html"}) {
            assertTrue(properties.ruleFor(path).isEmpty(), path + " must not be covered");
        }
    }
}
