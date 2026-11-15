package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
        @Size(min = 3, max = 16, message = "{validation.username.size16}")
        String username,

        @Email(message = "{validation.email.invalid}")
        String email,

        @Size(min = 2, max = 50, message = "{validation.firstName.size}")
        String firstName,

        @Size(min = 2, max = 50, message = "{validation.lastName.size}")
        String lastName,

        @Pattern(regexp = "^[0-9]+$", message = "{validation.phone.digits}")
        @Size(min = 10, max = 13, message = "{validation.phone.size}")
        String phoneNumber,

        String profileImage,

        Role role
) {
}
