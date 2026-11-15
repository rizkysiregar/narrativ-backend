package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.model.dto.RegisterOtpDTO;
import com.rizkysiregar.feedzmore.model.dto.RegistrationTokenDTO;

public interface IRegistrationOtpService {
    boolean isVerificationRequired();

    RegisterOtpDTO sendOtp(String email);

    RegistrationTokenDTO verifyOtp(String email, String otp);

    /** Checks the token issued by {@link #verifyOtp} for this email and burns it. */
    void consumeRegistrationToken(String email, String registrationToken);
}
