package com.rizkysiregar.feedzmore.service.auth;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.GoogleUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GoogleTokenVerifierService {

    @Value("${google.client-id}")
    private String clientId;

    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    public GoogleUserInfo verifyToken(String idToken) {
        try {
            String url = GOOGLE_TOKEN_INFO_URL + idToken;
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BusinessException(GlobalMessage.GOOGLE_UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }

            Map<String, String> payload = response.getBody();

            // ensure token was issue for feedzmore app
            String aud = (String) payload.get("aud");
            if (!clientId.equals(aud)) {
                throw new BusinessException(GlobalMessage.GOOGLE_TOKEN_MISS_MATCH, HttpStatus.UNAUTHORIZED);
            }

            return GoogleUserInfo.builder()
                    .email(payload.get("email"))
                    .firstName(payload.get("given_name"))
                    .lastName(payload.get("family_name"))
                    .picture(payload.get("picture"))
                    .googleId(payload.get("sub"))
                    .build();


        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(GlobalMessage.GOOGLE_TOKEN_FAILED_TO_VERIFY, HttpStatus.UNAUTHORIZED);
        }
    }

}
