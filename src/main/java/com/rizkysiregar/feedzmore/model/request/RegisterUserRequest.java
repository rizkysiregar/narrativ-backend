package com.rizkysiregar.feedzmore.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rizkysiregar.feedzmore.model.request.constraint.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

// phoneNumber is no longer collected at registration; apps that still send it must not get a 400, so it is ignored
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class RegisterUserRequest {
    @NotBlank(message = "{validation.username.required}")
    @NotEmpty(message = "{validation.username.empty}")
    @Size(min = 3, max = 16, message = "{validation.username.size15}")
    private String username;

    @NotBlank(message = "{validation.password.required}")
    @NotEmpty(message = "{validation.password.empty}")
    @Size(min = 8, max = 64, message = "{validation.password.size}")
    @MaxUtf8Bytes(value = 72, message = "{validation.password.tooLongHint}")
    @ToString.Exclude
    private String password;

    @NotBlank(message = "{validation.email.required}")
    @NotEmpty(message = "{validation.email.empty}")
    @Email(message = "{validation.email.invalid}")
    @ToString.Exclude
    private String email;

    @NotBlank(message = "{validation.firstName.required}")
    @NotEmpty(message = "{validation.firstName.empty}")
    @Size(min = 2, max = 50, message = "{validation.firstName.size}")
    private String firstName;

    @NotBlank(message = "{validation.lastName.required}")
    @NotEmpty(message = "{validation.lastName.empty}")
    @Size(min = 2, max = 50, message = "{validation.lastName.size}")
    private String lastName;

    private String profileImage;

    // issued by POST /auth/register/verify-otp; only asked for where email verification is switched on
    @ToString.Exclude
    private String registrationToken;
}
