package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    @Email
    @NotBlank
    String email;

    @NotBlank
    String resetToken;

    @NotBlank
    String newPassword;
}
