package com.rizkysiregar.feedzmore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;

/**
 * Firebase App Check: which requests must come from the genuine mobile app, and how strictly that is applied.
 * <p>
 * {@code mode} is the global switch. A rule may carry a {@code mode} of its own, but only to be milder than the
 * global one: the global mode is a ceiling, so setting it to {@code off} stops every check at once.
 *
 * @param mode          off: nothing is checked. log: requests are checked and the outcome is logged and counted,
 *                      none is refused. enforce: a request without a valid token is refused with 403.
 * @param projectNumber number (not the id) of the Firebase project the tokens belong to; required unless off
 * @param header        request header that carries the App Check token
 * @param rules         the paths that are checked; a path that matches no rule is never checked
 */
@ConfigurationProperties(prefix = "app.appcheck")
public record AppCheckProperties(
        @DefaultValue("off") Mode mode,
        @DefaultValue("") String projectNumber,
        @DefaultValue("X-Firebase-AppCheck") String header,
        @DefaultValue List<Rule> rules
) {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    public enum Mode {
        OFF, LOG, ENFORCE;

        /** The milder of the two. */
        public Mode capAt(Mode ceiling) {
            return ordinal() <= ceiling.ordinal() ? this : ceiling;
        }
    }

    /**
     * @param paths       Ant patterns, relative to the context path
     * @param allowedApps Firebase app ids that may call these paths; empty accepts any app of the project
     * @param mode        optional, milder than the global mode; empty follows the global mode
     */
    public record Rule(List<String> paths, @DefaultValue List<String> allowedApps, Mode mode) {

        boolean matches(String path) {
            return paths != null && paths.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
        }
    }

    /** The first rule whose paths match; null when the path is not covered. */
    public Optional<Rule> ruleFor(String path) {
        return rules.stream().filter(rule -> rule.matches(path)).findFirst();
    }
}
