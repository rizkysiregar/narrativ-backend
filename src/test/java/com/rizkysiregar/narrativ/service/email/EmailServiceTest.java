package com.rizkysiregar.narrativ.service.email;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setup() {}


    @Test
    void testSendEmail_Success() {
        // Arrange
        String to = "test-to@mail.com";
        String subject = "Test Email";
        String body = "This is a test email";

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        // Act
        emailService.sendEmail(to, subject, body);

        // Assert
        Mockito.verify(mailSender, Mockito.times(1)).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertArrayEquals(new String[]{to}, sent.getTo());
        assertEquals(subject, sent.getSubject());
        assertEquals(body, sent.getText());
        assertEquals("monostacklabs.studio@gmail.com", sent.getFrom());
    }


    @Test
    void testSendEmail_Failure_ShouldThrowBusinessException() {
        // Arrange
        Mockito.doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Act & Assert
        assertThrows(com.rizkysiregar.narrativ.exception.BusinessException.class,
                () -> emailService.sendEmail("to@mail.com", "Subject", "Body"));

        Mockito.verify(mailSender, Mockito.times(1)).send(any(SimpleMailMessage.class));
    }


}
