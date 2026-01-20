package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterDeviceRequest {
    @NotBlank
    private String pushToken;

    @NotBlank
    private String platform;

    private String deviceId;
    private String appVersion;
    private String osVersion;
    private String deviceModel;
    private String locale;
    private String timezone;
}
