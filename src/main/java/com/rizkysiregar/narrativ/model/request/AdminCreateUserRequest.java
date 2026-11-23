package com.rizkysiregar.narrativ.model.request;

import com.rizkysiregar.narrativ.model.request.constraint.MaxUtf8Bytes;
import com.rizkysiregar.narrativ.model.request.constraint.UsernameRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An account an admin creates for a user (POST /admin/users), with the same rules as a sign-up. No OTP: the admin is
 * signed in, and the account is flagged as seeded. The admin picks the password to sign in with it in the apps.
 */
public record AdminCreateUserRequest(
        @NotBlank(message = "{validation.username.required}")
        @Size(min = 3, max = 16, message = "{validation.username.size15}")
        @Pattern(regexp = UsernameRules.PATTERN, message = "{validation.username.pattern}")
        String username,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, max = 64, message = "{validation.password.size}")
        @MaxUtf8Bytes(value = 72, message = "{validation.password.tooLongHint}")
        String password,

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.firstName.required}")
        @Size(min = 2, max = 50, message = "{validation.firstName.size}")
        String firstName,

        @NotBlank(message = "{validation.lastName.required}")
        @Size(min = 2, max = 50, message = "{validation.lastName.size}")
        String lastName
) {
    @Override
    public String toString() {
        // never log the password or the email
        return "AdminCreateUserRequest[username=" + username + "]";
    }
}
