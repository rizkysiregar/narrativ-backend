package com.rizkysiregar.feedzmore.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class OtpCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String otp;
    private LocalDateTime expirationTime;

    public OtpCode() {}

    public OtpCode(String email, String otp, LocalDateTime expirationTime) {
        this.email = email;
        this.otp = otp;
        this.expirationTime = expirationTime;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expirationTime);
    }
}
