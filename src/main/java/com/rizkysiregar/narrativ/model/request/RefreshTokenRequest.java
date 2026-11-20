package com.rizkysiregar.narrativ.model.request;

import lombok.Data;
import lombok.ToString;

@Data
public class RefreshTokenRequest {
    // optional: a web client sends no body and uses the refresh cookie instead (see WebSessionCookie)
    @ToString.Exclude
    private String refreshToken;
}
