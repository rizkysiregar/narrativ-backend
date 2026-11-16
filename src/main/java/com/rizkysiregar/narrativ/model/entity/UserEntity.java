package com.rizkysiregar.narrativ.model.entity;

import com.rizkysiregar.narrativ.model.enums.Role;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@Entity(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 16)
    @NotBlank(message = "{validation.username.required}")
    @NotEmpty(message = "{validation.username.empty}")
    @Size(min = 3, max = 16, message = "{validation.username.size15}")
    private String username;

    @NotBlank(message = "{validation.password.required}")
    @NotEmpty(message = "{validation.password.empty}")
    @Size(min = 3)
    private String password;

    @Column(nullable = false, unique = true)
    @Email(message = "{validation.email.invalid}")
    @NotBlank(message = "{validation.email.required}")
    private String email;

    @Column(length = 50)
    @NotEmpty(message = "{validation.firstName.empty}")
    private String firstName;

    @Column(length = 50)
    @NotEmpty(message = "{validation.lastName.empty}")
    private String lastName;

    @Column(length = 13)
    @Pattern(regexp = "^[0-9]+$", message = "{validation.phone.digits}")
    @Size(min = 10, max = 13, message = "{validation.phone.size}")
    private String phoneNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private String profileImage;

    @Column(name = "google_id", unique = true)
    private String googleId; // null for regular username/password

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    // admin moderation, independent of isDeleted (which the user controls by deleting the account)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "banned_at")
    private Instant bannedAt;

    @Column(name = "banned_by")
    private UUID bannedBy;

    @Column(name = "ban_reason", length = 500)
    private String banReason;

}
