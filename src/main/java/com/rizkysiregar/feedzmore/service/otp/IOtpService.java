package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.util.Operation;

public interface IOtpService {
    public Operation sendOtp(String email);
    public boolean verifyOtp(String email, String otp);
}
