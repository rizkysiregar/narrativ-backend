package com.rizkysiregar.feedzmore.service.email;

public interface IEmailService {
    public void sendEmail(String to, String subject, String body);
    void sendOtpEmail(String to, String otp);
}
