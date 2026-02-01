package com.rizkysiregar.feedzmore.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 16)
    @NotBlank(message = "Username is required")
    @NotEmpty(message = "Username cannot be empty")
    @Size(min = 3, max = 16, message = "The length of the username should be between 3 and 15")
    private String username;

    private String password;

    @Column(nullable = false, unique = true)
    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is required")
    private String email;

    @Column(length = 50)
    @NotEmpty(message = "First name cannot be empty")
    private String firstName;

    @Column(length = 50)
    @NotEmpty(message = "Last name cannot be empty")
    private String lastName;

    @Column(length = 13)
    @Pattern(regexp = "^[0-9]+$", message = "Phone number must contain only digits")
    @Size(min = 10, max = 13, message = "Phone number must be between 10 and 13 digits")
    private String phoneNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private String profileImage;

}
