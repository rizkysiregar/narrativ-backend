package com.rizkysiregar.narrativ.service.otp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpChallengeTest {

    @Test
    void generateOtp_IsSixDigits() {
        for (int i = 0; i < 200; i++) {
            assertTrue(OtpChallenge.generateOtp().matches("\\d{6}"));
        }
    }
}
