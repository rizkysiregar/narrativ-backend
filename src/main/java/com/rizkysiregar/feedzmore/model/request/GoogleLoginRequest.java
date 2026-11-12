package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class GoogleLoginRequest {
    @NotBlank
    @ToString.Exclude
    private String idToken; // id token from Google Sign in SDK on android
}
