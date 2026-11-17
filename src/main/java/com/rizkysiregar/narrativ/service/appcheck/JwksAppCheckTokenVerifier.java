package com.rizkysiregar.narrativ.service.appcheck;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Verifies App Check tokens the way Firebase documents it for backends without an Admin SDK method (the Java
 * SDK has none): the signature against Google's public keys (RS256, key picked by {@code kid}), the issuer
 * {@code https://firebaseappcheck.googleapis.com/<project number>}, an audience that holds
 * {@code projects/<project number>}, the expiry, and a non-empty subject, which is the Firebase app id.
 * The decoder caches the key set, so a request normally costs no network call.
 */
public class JwksAppCheckTokenVerifier implements AppCheckTokenVerifier {

    static final String JWKS_URI = "https://firebaseappcheck.googleapis.com/v1/jwks";
    static final String ISSUER_PREFIX = "https://firebaseappcheck.googleapis.com/";
    private static final Duration KEY_FETCH_TIMEOUT = Duration.ofSeconds(3);

    private final JwtDecoder decoder;

    public JwksAppCheckTokenVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    public static JwksAppCheckTokenVerifier create(String projectNumber) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(KEY_FETCH_TIMEOUT);
        requestFactory.setReadTimeout(KEY_FETCH_TIMEOUT);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        decoder.setJwtValidator(validators(projectNumber));
        return new JwksAppCheckTokenVerifier(decoder);
    }

    public static OAuth2TokenValidator<Jwt> validators(String projectNumber) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER_PREFIX + projectNumber),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains("projects/" + projectNumber)),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank())
        );
    }

    @Override
    public String verify(String token) throws InvalidTokenException, UnavailableException {
        try {
            return decoder.decode(token).getSubject();
        } catch (BadJwtException e) {
            throw new InvalidTokenException(e.getMessage(), e);
        } catch (JwtException e) {
            // not the token's fault: the key set could not be fetched
            throw new UnavailableException(e.getMessage(), e);
        }
    }
}
