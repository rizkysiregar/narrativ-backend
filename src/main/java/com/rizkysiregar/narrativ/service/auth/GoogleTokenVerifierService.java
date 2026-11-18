package com.rizkysiregar.narrativ.service.auth;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.GoogleUserInfo;
import com.rizkysiregar.narrativ.util.EmailNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Verifies Google ID tokens locally, the way Google recommends for production: the signature against Google's
 * public keys (RS256, cached by the decoder, so a login normally costs no network call), the issuer and the expiry
 * here, the audience and the verified email in {@link #toVerifiedUser}. The tokeninfo endpoint that was used before
 * is meant for debugging, is rate limited and was called without a timeout.
 */
@Service
@Slf4j
public class GoogleTokenVerifierService {

    static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration KEY_FETCH_TIMEOUT = Duration.ofSeconds(3);

    @Value("${google.client-id}")
    private String clientId;

    private final JwtDecoder decoder;

    public GoogleTokenVerifierService() {
        this(createDecoder());
    }

    GoogleTokenVerifierService(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    private static JwtDecoder createDecoder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(KEY_FETCH_TIMEOUT);
        requestFactory.setReadTimeout(KEY_FETCH_TIMEOUT);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        decoder.setJwtValidator(validators());
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validators() {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, issuer -> issuer != null && ISSUERS.contains(issuer.toString()))
        );
    }

    public GoogleUserInfo verifyToken(String idToken) {
        return toVerifiedUser(decode(idToken).getClaims());
    }

    // the checks on the claims, apart from the signature, issuer and expiry the decoder already verified
    GoogleUserInfo toVerifiedUser(Map<String, Object> payload) {
        validateAudience(payload);
        validateEmail(payload);
        return mapToGoogleInfo(payload);
    }

    private Jwt decode(String idToken) {
        try {
            return decoder.decode(idToken);
        } catch (BadJwtException e) {
            log.warn("AUTH.GOOGLE.TOKEN.INVALID reason={}", e.getMessage());
            throw new BusinessException(GlobalMessage.GOOGLE_UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        } catch (JwtException e) {
            // not the token's fault: Google's key set could not be fetched
            log.error("AUTH.GOOGLE.KEYS.UNAVAILABLE reason={}", e.getMessage());
            throw new BusinessException(GlobalMessage.GOOGLE_TOKEN_FAILED_TO_VERIFY, HttpStatus.UNAUTHORIZED);
        }
    }

    // a decoded JWT carries the audience as a list, tokeninfo used to answer a single string
    private void validateAudience(Map<String, Object> payload) {
        Object aud = payload.get("aud");
        boolean matches = aud instanceof Collection<?> audiences
                ? audiences.contains(clientId)
                : clientId.equals(aud);
        if (!matches) {
            throw new BusinessException(
                    GlobalMessage.GOOGLE_TOKEN_MISS_MATCH,
                    HttpStatus.UNAUTHORIZED
            );
        }
    }


    // An account is matched by email, so the email must be one Google vouches for: anybody can put an address
    // they do not own on a Google account, and Google then answers email_verified=false.
    private void validateEmail(Map<String, Object> payload) {
        Object email = payload.get("email");
        if (!(email instanceof String address) || address.isBlank()) {
            throw new BusinessException(GlobalMessage.GOOGLE_UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        if (!"true".equalsIgnoreCase(String.valueOf(payload.get("email_verified")))) {
            throw new BusinessException(GlobalMessage.GOOGLE_EMAIL_NOT_VERIFIED, HttpStatus.UNAUTHORIZED);
        }
    }

    private GoogleUserInfo mapToGoogleInfo(Map<String, Object> payload) {
        return GoogleUserInfo.builder()
                .email(EmailNormalizer.normalize((String) payload.get("email")))
                .firstName((String) payload.get("given_name"))
                .lastName((String) payload.get("family_name"))
                .picture((String) payload.get("picture"))
                .googleId((String) payload.get("sub"))
                .build();
    }
}
