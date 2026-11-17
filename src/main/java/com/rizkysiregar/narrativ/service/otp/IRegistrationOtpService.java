package com.rizkysiregar.narrativ.service.otp;

import com.rizkysiregar.narrativ.model.dto.RegisterOtpDTO;
import com.rizkysiregar.narrativ.model.dto.RegistrationTokenDTO;

public interface IRegistrationOtpService {
    boolean isVerificationRequired();

    RegisterOtpDTO sendOtp(String email);

    RegistrationTokenDTO verifyOtp(String email, String otp);

    /** Checks the token issued by {@link #verifyOtp} for this email and burns it. */
    void consumeRegistrationToken(String email, String registrationToken);
}
