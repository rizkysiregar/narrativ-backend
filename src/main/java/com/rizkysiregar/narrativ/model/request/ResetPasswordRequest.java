package com.rizkysiregar.narrativ.model.request;

import com.rizkysiregar.narrativ.model.request.constraint.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    @Email
    @NotBlank
    @ToString.Exclude
    String email;

    @NotBlank
    @ToString.Exclude
    String resetToken;

    @NotBlank
    @Size(min = 8, max = 64, message = "{validation.password.size}")
    @MaxUtf8Bytes(value = 72, message = "{validation.password.tooLongHint}")
    @ToString.Exclude
    String newPassword;
}
