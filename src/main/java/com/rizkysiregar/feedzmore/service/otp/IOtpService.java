package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.model.dto.SentOtpDTO;
import com.rizkysiregar.feedzmore.util.Operation;

public interface IOtpService {
    public SentOtpDTO sendOtp(String email);
    public boolean verifyOtp(String email, String otp);
}
