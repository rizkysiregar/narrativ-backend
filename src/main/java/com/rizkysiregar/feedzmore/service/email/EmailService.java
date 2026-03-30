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
    public void sendEmail(String to, String subject, String body) {
        try {
            log.info("SEND.EMAIL :{}, subject:{}, body:{}", to, subject, body);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("feedzmore.helpdesk@gmail.com");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("EmailService.sendEmail {}",  e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean sendOtpEmail(String to, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Your One-Time Password (OTP)");

            // prepare the email context
            Context context = new Context();
            context.setVariable("otp", otpCode);

            // Process HTML with thymeleaf
            String htmlContent = templateEngine.process("email/otp-email.html", context);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("EmailService.sendOtpEmail {} ", e.getMessage());
            return false;
        }
    }
}
