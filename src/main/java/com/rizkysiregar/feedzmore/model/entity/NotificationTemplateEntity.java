package com.rizkysiregar.feedzmore.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_template")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplateEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    private String title;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private String mode; // STATIC, TEMPLATE, DIRECT

    @Column(nullable = false)
    private String category; // CASUAL, EVENT, ADMIN, OTP

    private String topic;

    private boolean isActive = true;

    private Instant createdAt = Instant.now();
}
