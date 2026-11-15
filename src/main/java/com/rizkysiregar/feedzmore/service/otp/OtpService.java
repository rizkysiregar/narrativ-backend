package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.util.RedisUtil;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.ResetTokenDTO;
import com.rizkysiregar.feedzmore.model.dto.SentOtpDTO;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import com.rizkysiregar.feedzmore.util.EmailMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class OtpService implements IOtpService {

    private final IEmailService emailService;
    private final UserRepository userRepository;
    private final RedisUtil redisUtil;

    private static final long OTP_TTL_SECONDS = 60;

    private final OtpChallenge challenge;

    public OtpService(IEmailService emailService, UserRepository userRepository, RedisUtil redisUtil) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
        this.challenge = new OtpChallenge(redisUtil, "otp", OTP_TTL_SECONDS);
    }

    @Override
    public SentOtpDTO sendOtp(String email) {
        challenge.assertNotLocked(email);

        if (challenge.hasPendingOtp(email)) {
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.TOO_MANY_REQUESTS);
        }

        String otp = OtpChallenge.generateOtp();

        // The answer is the same whether or not the email is registered, otherwise this endpoint tells anybody which
        // emails have an account. An unknown email gets an OTP that nobody receives: it is kept like a real one, so
        // asking again or guessing it behaves exactly the same (too many requests, wrong code, lock after 5 guesses).
        boolean registered = userRepository.findByEmail(email).filter(user -> !user.isDeleted()).isPresent();
        if (registered) {
            boolean sendStatus = emailService.sendOtpEmail(email, otp);
            if (!sendStatus) {
                log.error("## Failed to send OTP to email: {}", EmailMasking.maskEmail(email));
                throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            log.info("## OTP requested for an email without an account: {}", EmailMasking.maskEmail(email));
        }

        challenge.store(email, otp);

        return SentOtpDTO.builder()
                .otpSendToEmailAddress(EmailMasking.maskEmail(email))
                .expirationTimeInMinute("1")
                .build();

    }

    @Override
    public ResetTokenDTO verifyOtp(String email, String otp) {
        challenge.verify(email, otp);

        String resetToken = UUID.randomUUID().toString();
        redisUtil.set("reset_token:".concat(email), resetToken, 600);

        return ResetTokenDTO.builder()
                .resetToken(resetToken)
                .build();
    }

}
