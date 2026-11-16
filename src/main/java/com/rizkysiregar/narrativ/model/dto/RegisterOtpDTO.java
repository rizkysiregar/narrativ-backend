package com.rizkysiregar.narrativ.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/** The OTP fields are empty when the environment does not verify emails; the app then skips the OTP step. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterOtpDTO {
    boolean verificationRequired;
    String otpSendToEmailAddress;
    String expirationTimeInMinute;
}
