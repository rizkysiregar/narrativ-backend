package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {
    @NotBlank
    private String idToken; // id token from Google Sign in SDK on android
}
