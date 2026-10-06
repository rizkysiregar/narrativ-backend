package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    @Email
    @NotBlank
    String email;

    @Size(min = 6 ,max = 6, message = "OTP must be exactly 6 digits")
    @NotBlank
    String otp;
}
