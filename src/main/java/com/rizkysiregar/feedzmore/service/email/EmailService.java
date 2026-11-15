package com.rizkysiregar.feedzmore.service.email;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import com.rizkysiregar.feedzmore.i18n.LocalizedMessages;
import org.springframework.context.i18n.LocaleContextHolder;
import com.rizkysiregar.feedzmore.util.EmailMasking;

@Service
@Slf4j
public class EmailService implements IEmailService {

//    @Value("${mail.username}")
//    String fromEmail;

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendEmailTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        log.debug("EMAIL.SEND.TEMPLATE to={} subject={} template={}", EmailMasking.maskEmail(to), subject, templateName);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);

            // email context
            Context context = new Context(LocaleContextHolder.getLocale());
            if (variables != null) {
                variables.forEach(context::setVariable);
            }

            String htmlContent = templateEngine.process("email/"+templateName, context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (Exception e) {
            log.error("EMAIL.SEND.FAILED kind=template reason={}", e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            log.debug("EMAIL.SEND to={} subject={}", EmailMasking.maskEmail(to), subject);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("feedzmore.helpdesk@gmail.com");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("EMAIL.SEND.FAILED kind=plain reason={}", e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean sendOtpEmail(String to, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(LocalizedMessages.text(
                    "email.otp.subject", LocaleContextHolder.getLocale(), "Your One-Time Password (OTP)"));

            // prepare the email context
            Context context = new Context(LocaleContextHolder.getLocale());
            context.setVariable("otp", otpCode);

            // Process HTML with thymeleaf
            String htmlContent = templateEngine.process("email/otp-email.html", context);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("EMAIL.SEND.FAILED kind=otp reason={}", e.getMessage());
            return false;
        }
    }
}
