package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.RegisterOtpDTO;
import com.rizkysiregar.feedzmore.model.dto.RegistrationTokenDTO;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import com.rizkysiregar.feedzmore.util.EmailMasking;
import com.rizkysiregar.feedzmore.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Proves that the person registering can read the mailbox: an OTP is sent to the email, and the right OTP is traded
 * for a registration token that POST /auth/register asks for. Switched per environment with
 * app.registration.email-verification.enabled (off in dev, on in prod).
 */
@Slf4j
@Service
public class RegistrationOtpService implements IRegistrationOtpService {

    private static final long OTP_TTL_SECONDS = 5 * 60;
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final long REGISTRATION_TOKEN_TTL_SECONDS = 10 * 60;
    private static final String COOLDOWN_KEY_PREFIX = "register_otp_cooldown:";
    private static final String TOKEN_KEY_PREFIX = "register_token:";

    private final IEmailService emailService;
    private final UserRepository userRepository;
    private final RedisUtil redisUtil;
    private final OtpChallenge challenge;
    private final boolean verificationEnabled;

    public RegistrationOtpService(IEmailService emailService,
                                  UserRepository userRepository,
                                  RedisUtil redisUtil,
                                  Environment environment,
                                  @Value("${app.registration.email-verification.enabled}") boolean verificationEnabled) {
        // prod hardcodes true, but an env var would still win over the yml: refuse to run unverified in prod
        if (!verificationEnabled && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "app.registration.email-verification.enabled must be true in the prod profile");
        }
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
        this.challenge = new OtpChallenge(redisUtil, "register_otp", OTP_TTL_SECONDS);
        this.verificationEnabled = verificationEnabled;
    }

    @Override
    public boolean isVerificationRequired() {
        return verificationEnabled;
    }

    @Override
    public RegisterOtpDTO sendOtp(String email) {
        if (!verificationEnabled) {
            return RegisterOtpDTO.builder().verificationRequired(false).build();
        }

        // register answers the same, so this does not tell more about which emails have an account
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(GlobalMessage.EMAIL_ALREADY_TAKEN, HttpStatus.BAD_REQUEST);
        }

        challenge.assertNotLocked(email);

        // atomic, so parallel requests cannot all get past the cooldown and send several mails
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        if (!redisUtil.setIfAbsent(cooldownKey, "1", RESEND_COOLDOWN_SECONDS)) {
            throw new BusinessException(GlobalMessage.OTP_RESEND_COOLDOWN, HttpStatus.TOO_MANY_REQUESTS);
        }

        String otp = OtpChallenge.generateOtp();
        if (!emailService.sendOtpEmail(email, otp)) {
            log.error("## Failed to send registration OTP to email: {}", EmailMasking.maskEmail(email));
            redisUtil.delete(cooldownKey); // nothing was sent, so the user may try again right away
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        challenge.store(email, otp);

        return RegisterOtpDTO.builder()
                .verificationRequired(true)
                .otpSendToEmailAddress(EmailMasking.maskEmail(email))
                .expirationTimeInMinute(String.valueOf(OTP_TTL_SECONDS / 60))
                .build();
    }

    @Override
    public RegistrationTokenDTO verifyOtp(String email, String otp) {
        if (!verificationEnabled) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        challenge.verify(email, otp);

        String registrationToken = UUID.randomUUID().toString();
        redisUtil.set(TOKEN_KEY_PREFIX + email, registrationToken, REGISTRATION_TOKEN_TTL_SECONDS);
        return RegistrationTokenDTO.builder().registrationToken(registrationToken).build();
    }

    @Override
    public void consumeRegistrationToken(String email, String registrationToken) {
        String tokenKey = TOKEN_KEY_PREFIX + email;
        Object actual = redisUtil.get(tokenKey);

        if (actual == null || registrationToken == null || !MessageDigest.isEqual(
                actual.toString().getBytes(StandardCharsets.UTF_8),
                registrationToken.getBytes(StandardCharsets.UTF_8))) {
            log.info("## registrationToken invalid for {}", EmailMasking.maskEmail(email));
            throw new BusinessException(GlobalMessage.EMAIL_NOT_VERIFIED, HttpStatus.BAD_REQUEST);
        }
        // one time use
        redisUtil.delete(tokenKey);
    }
}
