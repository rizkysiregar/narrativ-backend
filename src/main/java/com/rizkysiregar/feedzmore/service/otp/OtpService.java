package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.model.OtpCode;
import com.rizkysiregar.feedzmore.model.dto.SentOtpDTO;
import com.rizkysiregar.feedzmore.repository.OtpCodeRepository;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import com.rizkysiregar.feedzmore.util.EmailMasking;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class OtpService implements IOtpService {

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @Autowired
    private IEmailService emailService;

    @Override
    public SentOtpDTO sendOtp(String email) {
        String otp = generateOtp();
        LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(5); // set expires to 5 min

        OtpCode otpCode = new OtpCode(email, otp, expirationTime);
        otpCodeRepository.save(otpCode);

        String message = "Your password reset OTP is: " + otp;
        emailService.sendEmail(email, "Password Reset OTP", message);
        return SentOtpDTO.builder()
                .otpSendToEmailAddress(EmailMasking.maskEmail(email))
                .expirationTimeInMinute("5")
                .build();
//        return Operation.SENT;
    }

    private String generateOtp() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000)); // Generate 6 digit OTP code
    }

    @Override
    public boolean verifyOtp(String email, String otp) {
        return otpCodeRepository.findByEmail(email)
                .filter(code -> !code.isExpired() && code.getOtp().equals(otp))
                .isPresent();
    }
}
