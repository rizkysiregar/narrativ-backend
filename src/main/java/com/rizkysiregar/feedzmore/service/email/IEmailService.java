package com.rizkysiregar.feedzmore.service.email;

public interface IEmailService {
    void sendEmail(String to, String subject, String body);

    boolean sendOtpEmail(String to, String otp);
}
