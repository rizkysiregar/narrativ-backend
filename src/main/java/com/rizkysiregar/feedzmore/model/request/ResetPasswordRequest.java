package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResetPasswordRequest {
    @Email
    @NotBlank
    String email;

    @NotBlank
    String resetToken;

    @NotBlank
    String newPassword;
}
