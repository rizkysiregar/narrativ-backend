package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserRequest {
    @NotBlank(message = "Username is required")
    @NotEmpty(message = "Username cannot be empty")
    @Size(min = 3, max = 16, message = "The length of the username should be between 3 and 15")
    private String username;

    @NotBlank(message = "Password is required")
    @NotEmpty(message = "Password cannot be empty")
    @Size(min = 6, max = 16, message = "The length of the password should be between 6 and 15")
    private String password;

    @NotBlank(message = "Email is required")
    @NotEmpty(message = "Email cannot be empty")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "First name is required")
    @NotEmpty(message = "First name cannot be empty")
    @Size(min = 2, max = 50, message = "The length of the first name should be between 2 and 50")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @NotEmpty(message = "Last name cannot be empty")
    @Size(min = 2, max = 50, message = "The length of the last name should be between 2 and 50")
    private String lastName;

    @NotBlank(message = "Phone number is required")
    @NotEmpty(message = "Phone number cannot be empty")
    @Size(min = 10, max = 13, message = "Phone number must be between 10 and 13 digits")
    private String phoneNumber;
    
    private String profileImage;
}
