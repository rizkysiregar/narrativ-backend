package com.rizkysiregar.narrativ.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_device", uniqueConstraints = {@UniqueConstraint(columnNames = "push_token")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeviceEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(length = 255)
    private String deviceId;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pushToken;

    private String appVersion;
    private String osVersion;
    private String deviceModel;
    private String locale;
    private String timezone;

    private boolean isActive;
    private LocalDateTime lastSeenAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updateAt;
}
