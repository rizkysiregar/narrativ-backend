package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;

@Data
public class RegisterDeviceRequest {
    @NotBlank
    @ToString.Exclude
    private String pushToken;

    // ANDROID and WEB today; the web client's pushes carry a link to open (see FirebaseNotificationService)
    @NotBlank
    @Pattern(regexp = "(?i)android|ios|web", message = "{validation.platform.invalid}")
    private String platform;

    private String deviceId;
    private String appVersion;
    private String osVersion;
    private String deviceModel;
    private String locale;
    private String timezone;
}
