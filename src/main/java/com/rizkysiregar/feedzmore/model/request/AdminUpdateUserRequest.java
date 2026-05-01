package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
        @Size(min = 3, max = 16, message = "The length of the username should be between 3 and 16")
        String username,

        @Email(message = "Email should be valid")
        String email,

        @Size(min = 2, max = 50, message = "The length of the first name should be between 2 and 50")
        String firstName,

        @Size(min = 2, max = 50, message = "The length of the last name should be between 2 and 50")
        String lastName,

        @Pattern(regexp = "^[0-9]+$", message = "Phone number must contain only digits")
        @Size(min = 10, max = 13, message = "Phone number must be between 10 and 13 digits")
        String phoneNumber,

        String profileImage,

        Role role
) {
}
