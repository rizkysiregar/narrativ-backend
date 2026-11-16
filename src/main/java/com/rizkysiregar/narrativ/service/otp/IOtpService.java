package com.rizkysiregar.narrativ.service.otp;

import com.rizkysiregar.narrativ.model.dto.ResetTokenDTO;
import com.rizkysiregar.narrativ.model.dto.SentOtpDTO;

public interface IOtpService {
    public SentOtpDTO sendOtp(String email);
    public ResetTokenDTO verifyOtp(String email, String otp);
}
