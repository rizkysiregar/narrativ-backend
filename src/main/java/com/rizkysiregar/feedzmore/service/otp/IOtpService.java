package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.model.dto.ResetTokenDTO;
import com.rizkysiregar.feedzmore.model.dto.SentOtpDTO;

public interface IOtpService {
    public SentOtpDTO sendOtp(String email);
    public ResetTokenDTO verifyOtp(String email, String otp);
}
