package com.rizkysiregar.feedzmore.service.auth;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.GoogleUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class GoogleTokenVerifierService {

    @Value("${google.client-id}")
    private String clientId;

    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";
    private final RestClient restClient = RestClient.create();

    public GoogleUserInfo verifyToken(String idToken) {
        return toVerifiedUser(fetchTokenInfo(idToken));
    }

    // the checks on what Google answered, apart from the call itself
    GoogleUserInfo toVerifiedUser(Map<String, Object> payload) {
        validateAudience(payload);
        validateEmail(payload);
        return mapToGoogleInfo(payload);
    }

    private Map<String, Object> fetchTokenInfo(String idToken) {
        try {
            return restClient.get()
                    .uri(GOOGLE_TOKEN_INFO_URL + "?id_token={token}", idToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new BusinessException(
                                GlobalMessage.GOOGLE_UNAUTHORIZED, HttpStatus.UNAUTHORIZED
                        );
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    GlobalMessage.GOOGLE_TOKEN_FAILED_TO_VERIFY, HttpStatus.UNAUTHORIZED
            );
        }
    }


    private void validateAudience(Map<String, Object> payload) {
        String aud = (String) payload.get("aud");
        if (!clientId.equals(aud)) {
            throw new BusinessException(
                    GlobalMessage.GOOGLE_TOKEN_MISS_MATCH,
                    HttpStatus.UNAUTHORIZED
            );
        }
    }


    // An account is matched by email, so the email must be one Google vouches for: anybody can put an address
    // they do not own on a Google account, and Google then answers email_verified=false (as the string "false").
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
                .email((String) payload.get("email"))
                .firstName((String) payload.get("given_name"))
                .lastName((String) payload.get("family_name"))
                .picture((String) payload.get("picture"))
                .googleId((String) payload.get("sub"))
                .build();
    }
}
