package com.rizkysiregar.narrativ.service.otp;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.SentOtpDTO;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.request.ConfirmEmailChangeRequest;
import com.rizkysiregar.narrativ.model.response.LoginResponse;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.AuthService;
import com.rizkysiregar.narrativ.service.email.IEmailService;
import com.rizkysiregar.narrativ.util.AfterCommit;
import com.rizkysiregar.narrativ.util.EmailMasking;
import com.rizkysiregar.narrativ.util.EmailNormalizer;
import com.rizkysiregar.narrativ.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Changes the email of the signed in user in two steps. First an OTP goes to the new address, which proves the
 * user can read it. Then the change is confirmed with that OTP and the current password (or a fresh Google id
 * token), so a stolen access token alone cannot move the account to another mailbox. On success every other
 * session is ended, the caller gets a fresh one, and the old address is told about the change.
 * <p>
 * The OTP is bound to the user and the new address together, so it cannot be used by anybody else or for another
 * address.
 */
@Slf4j
@Service
public class EmailChangeService {

    private static final long OTP_TTL_SECONDS = 5 * 60;
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final String COOLDOWN_KEY_PREFIX = "email_change_otp_cooldown:";

    private final UserRepository userRepository;
    private final AuthService authService;
    private final IEmailService emailService;
    private final RedisUtil redisUtil;
    private final OtpChallenge challenge;

    public EmailChangeService(UserRepository userRepository, AuthService authService, IEmailService emailService,
                              RedisUtil redisUtil) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.emailService = emailService;
        this.redisUtil = redisUtil;
        this.challenge = new OtpChallenge(redisUtil, "email_change_otp", OTP_TTL_SECONDS);
    }

    public SentOtpDTO sendOtp(UUID userId, String newEmail) {
        UserEntity user = findActiveUser(userId);
        String email = EmailNormalizer.normalize(newEmail);
        assertChangeable(user, email);

        String key = challengeKey(userId, email);
        challenge.assertNotLocked(key);

        // atomic, so parallel requests cannot all get past the cooldown and send several mails
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;
        if (!redisUtil.setIfAbsent(cooldownKey, "1", RESEND_COOLDOWN_SECONDS)) {
            throw new BusinessException(GlobalMessage.OTP_RESEND_COOLDOWN, HttpStatus.TOO_MANY_REQUESTS);
        }

        String otp = OtpChallenge.generateOtp();
        if (!emailService.sendOtpEmail(email, otp)) {
            log.error("EMAIL.CHANGE.OTP.SEND.FAILED userId={} email={}", userId, EmailMasking.maskEmail(email));
            redisUtil.delete(cooldownKey); // nothing was sent, so the user may try again right away
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        challenge.store(key, otp);
        log.info("EMAIL.CHANGE.OTP.SENT userId={} email={}", userId, EmailMasking.maskEmail(email));

        return SentOtpDTO.builder()
                .otpSendToEmailAddress(EmailMasking.maskEmail(email))
                .expirationTimeInMinute(String.valueOf(OTP_TTL_SECONDS / 60))
                .build();
    }

    @Transactional
    public LoginResponse confirm(UUID userId, ConfirmEmailChangeRequest request) {
        UserEntity user = findActiveUser(userId);
        String email = EmailNormalizer.normalize(request.email());

        // identity first: without the password a stolen token cannot even spend the OTP attempts
        if (!authService.confirmsIdentity(user, request.password(), request.idToken())) {
            throw new BusinessException(GlobalMessage.CONFIRMATION_FAILED, HttpStatus.FORBIDDEN);
        }
        challenge.verify(challengeKey(userId, email), request.otp());
        // checked again: the address may have been registered since the OTP was sent
        assertChangeable(user, email);

        String previous = user.getEmail();
        user.setEmail(email);
        userRepository.save(user);
        log.info("EMAIL.CHANGED userId={} from={} to={}", userId, EmailMasking.maskEmail(previous), EmailMasking.maskEmail(email));

        // whoever else held a session (maybe the reason for the change) is signed out, this device carries on
        authService.revokeRefreshTokens(userId);
        LoginResponse session = authService.startSession(user);

        AfterCommit.run("notify old address of email change " + userId, () -> emailService.sendEmail(previous,
                "Your Narrativ email was changed / Email Narrativ Anda telah diubah",
                """
                        The email of your Narrativ account @%s was changed to %s.
                        If you did not do this, reset your password right away and contact us.

                        Email akun Narrativ @%s telah diubah menjadi %s.
                        Jika ini bukan Anda, segera atur ulang kata sandi dan hubungi kami.
                        """.formatted(user.getUsername(), EmailMasking.maskEmail(email),
                        user.getUsername(), EmailMasking.maskEmail(email))));
        return session;
    }

    private void assertChangeable(UserEntity user, String email) {
        if (email.equals(EmailNormalizer.normalize(user.getEmail()))) {
            throw new BusinessException(GlobalMessage.EMAIL_UNCHANGED, HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new BusinessException(GlobalMessage.EMAIL_ALREADY_TAKEN, HttpStatus.BAD_REQUEST);
        }
    }

    private UserEntity findActiveUser(UUID userId) {
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private static String challengeKey(UUID userId, String email) {
        return userId + ":" + email;
    }
}
