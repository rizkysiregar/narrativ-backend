package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RegisterOtpRequest {
    @NotBlank
    @Email
    private String email;
}
