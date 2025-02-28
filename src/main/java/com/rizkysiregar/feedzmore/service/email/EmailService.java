package com.rizkysiregar.feedzmore.service.email;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService implements IEmailService {

//    @Value("${mail.username}")
//    String fromEmail;

    @Autowired
    JavaMailSender mailSender;

    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            log.info("## sendEmail to:" + to + ", subject:" + subject + ", body:" + body);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("feedzmore.helpdesk@gmail.com");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("## error send email: " + e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
