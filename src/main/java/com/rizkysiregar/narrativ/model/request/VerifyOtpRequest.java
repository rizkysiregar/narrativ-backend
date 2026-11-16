package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    @Email
    @NotBlank
    String email;

    @Size(min = 6 ,max = 6, message = "{validation.otp.digits}")
    @NotBlank
    @ToString.Exclude
    String otp;
}
