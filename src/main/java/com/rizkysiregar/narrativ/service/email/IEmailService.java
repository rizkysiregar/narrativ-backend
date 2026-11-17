package com.rizkysiregar.narrativ.service.email;

import java.util.Map;

public interface IEmailService {
    void sendEmailTemplate(String to, String subject, String templateName, Map<String, Object> variables);
    void sendEmail(String to, String subject, String body);
    boolean sendOtpEmail(String to, String otp);
}
